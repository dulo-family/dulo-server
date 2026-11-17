package com.dulo.core.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.place.port.in.ManagePlacesUseCase.PlaceContent;
import com.dulo.core.shared.port.in.HasLinksException;
import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryChangeEvents;
import com.dulo.core.testing.InMemoryPlaces;
import com.dulo.core.testing.InMemoryRoles;
import com.dulo.core.testing.InMemoryTrees;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.audit.Link;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.place.Coordinates;
import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.place.PlaceType;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class PlaceServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryPlaces places = new InMemoryPlaces();
    private final InMemoryChangeEvents events = new InMemoryChangeEvents();
    private final PlaceService service =
            new PlaceService(
                    this.places,
                    this.events,
                    new AccessPolicy(
                            this.users,
                            new InMemoryRoles(DefaultRoles.all()),
                            new InMemoryTrees(
                                    TreeSettings.create("Smiths", null, Set.of(Feature.PLACES))),
                            FeatureOverrides::none),
                    this.events.recorder(),
                    new DirectUnitOfWork());
    private final UserId editor =
            this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.EDITOR)).id();

    private static PlaceContent content(final String name, final @Nullable PlaceId enclosedBy) {
        return new PlaceContent(name, PlaceType.TOWN, null, enclosedBy);
    }

    @Test
    void createsReadsAndReplacesPlaces() {
        final Place county =
                this.service.createPlace(
                        this.editor,
                        new PlaceContent(
                                "Kreis Mitte",
                                PlaceType.COUNTY,
                                new Coordinates(48.2, 11.5),
                                null));
        final Place town = this.service.createPlace(this.editor, content("Dorf", county.id()));

        assertThat(this.service.place(this.editor, town.id())).isEqualTo(town);
        final Place renamed =
                this.service.replacePlace(
                        this.editor, town.id(), town.version(), content("Neudorf", null));
        assertThat(renamed.name()).isEqualTo("Neudorf");
        assertThat(renamed.enclosedBy()).isNull();
        assertThat(renamed.version()).isEqualTo(town.version() + 1);

        final List<ChangeEvent> history = this.service.placeHistory(this.editor, town.id());
        assertThat(history)
                .extracting(ChangeEvent::kind)
                .containsExactly(ChangeKind.CREATED, ChangeKind.CHANGED);
        assertThat(history.getLast().changes())
                .containsExactly(
                        new FieldChange("name", "Dorf", "Neudorf"),
                        new FieldChange("enclosedBy", county.id().toString(), null));
        assertThat(this.service.placeHistory(this.editor, county.id()).getFirst().changes())
                .contains(
                        new FieldChange(
                                "coordinates", null, Map.of("latitude", 48.2, "longitude", 11.5)));
    }

    @Test
    void pagesAndSearchesByName() {
        for (final String name : List.of("Bergen", "Aachen", "Celle", "Berlin")) {
            this.service.createPlace(this.editor, content(name, null));
        }

        final CursorPage<Place> first = this.service.places(this.editor, null, null, 2);
        final CursorPage<Place> second = this.service.places(this.editor, null, first.next(), 2);

        assertThat(first.items()).extracting(Place::name).containsExactly("Aachen", "Bergen");
        assertThat(second.items()).extracting(Place::name).containsExactly("Berlin", "Celle");
        assertThat(second.next()).isNull();
        assertThat(this.service.places(this.editor, " BER ", null, 50).items())
                .extracting(Place::name)
                .containsExactly("Bergen", "Berlin");
        assertThatThrownBy(() -> this.service.places(this.editor, null, "nonsense!", 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> this.service.places(this.editor, "x".repeat(101), null, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enclosingPlacesMustExistAndNeverLoop() {
        final Place country = this.service.createPlace(this.editor, content("Land", null));
        final Place county = this.service.createPlace(this.editor, content("Kreis", country.id()));
        final Place town = this.service.createPlace(this.editor, content("Stadt", county.id()));

        assertThatThrownBy(() ->
                        this.service.createPlace(this.editor, content("Nowhere", PlaceId.newId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doesn't exist");
        assertThatThrownBy(() ->
                        this.service.replacePlace(
                                this.editor,
                                country.id(),
                                country.version(),
                                content("Land", town.id())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lie in itself");
    }

    @Test
    void deletingAPlaceWithPlacesInItNeedsConfirmation() {
        final Place county = this.service.createPlace(this.editor, content("Kreis", null));
        final Place town = this.service.createPlace(this.editor, content("Stadt", county.id()));
        final List<Link> links =
                List.of(new Link(EntityType.PLACE, town.id().toString(), "enclosedBy"));

        assertThatThrownBy(() ->
                        this.service.deletePlace(this.editor, county.id(), county.version(), false))
                .isInstanceOfSatisfying(
                        HasLinksException.class, e -> assertThat(e.links()).isEqualTo(links));
        this.service.deletePlace(this.editor, county.id(), county.version(), true);

        assertThat(this.places.find(county.id())).isEmpty();
        assertThat(this.service.place(this.editor, town.id()).enclosedBy()).isNull();
        // The unlinking and the deletion are one action, and the deletion lists the links.
        final List<ChangeEvent> last = this.events.all().subList(2, 4);
        assertThat(last)
                .extracting(ChangeEvent::kind)
                .containsExactly(ChangeKind.CHANGED, ChangeKind.DELETED);
        assertThat(last.get(0).correlationId()).isEqualTo(last.get(1).correlationId());
        assertThat(last.get(1).removedLinks()).isEqualTo(links);
        // The history outlives the place.
        assertThat(this.service.placeHistory(this.editor, county.id()))
                .extracting(ChangeEvent::kind)
                .containsExactly(ChangeKind.CREATED, ChangeKind.DELETED);
        assertThatThrownBy(() -> this.service.place(this.editor, county.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void checksVersionsAccessAndExistence() {
        final Place town = this.service.createPlace(this.editor, content("Stadt", null));
        final UserId viewer =
                this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.VIEWER)).id();
        final UserId contributor =
                this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.CONTRIBUTOR)).id();

        assertThat(this.service.place(viewer, town.id())).isEqualTo(town);
        assertThatThrownBy(() -> this.service.createPlace(viewer, content("No", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.service.deletePlace(contributor, town.id(), town.version(), true))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.service.replacePlace(
                                this.editor, town.id(), town.version() + 1, content("X", null)))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() ->
                        this.service.deletePlace(this.editor, town.id(), town.version() + 1, true))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() -> this.service.place(this.editor, PlaceId.newId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> this.service.placeHistory(this.editor, PlaceId.newId()))
                .isInstanceOf(NotFoundException.class);
    }
}
