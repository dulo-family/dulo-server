package com.dulo.core.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.audit.recording.Replay;
import com.dulo.core.event.port.in.ManageEventsUseCase.EventContent;
import com.dulo.core.event.service.EventService;
import com.dulo.core.family.port.in.ManageFamiliesUseCase.FamilyContent;
import com.dulo.core.family.service.FamilyService;
import com.dulo.core.person.port.in.ManagePeopleUseCase.PersonContent;
import com.dulo.core.person.service.PersonFields;
import com.dulo.core.person.service.PersonService;
import com.dulo.core.place.port.in.ManagePlacesUseCase.PlaceContent;
import com.dulo.core.place.service.PlaceService;
import com.dulo.core.shared.port.in.RestoreConflictException;
import com.dulo.core.shared.port.in.VersionRequiredException;
import com.dulo.core.testing.Genealogy;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.Link;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventType;
import com.dulo.domain.event.Participant;
import com.dulo.domain.event.ParticipantRole;
import com.dulo.domain.family.Child;
import com.dulo.domain.family.ChildRelation;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.RelationshipType;
import com.dulo.domain.person.NameType;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonName;
import com.dulo.domain.person.Sex;
import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.place.PlaceType;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Restoring people, places, families and events to earlier states, and after deletions. */
class RestoreTest {

    private final Genealogy tree = new Genealogy();
    private final PersonService people = this.tree.personService();
    private final PlaceService places = this.tree.placeService();
    private final FamilyService families = this.tree.familyService();
    private final EventService events = this.tree.eventService();
    private final UserId editor = this.tree.user(DefaultRoles.EDITOR);

    private static PersonContent named(final String given) {
        return new PersonContent(
                List.of(PersonName.of(NameType.BIRTH, given, "Weber")), Sex.UNKNOWN, null);
    }

    private static EventContent event(
            final @Nullable PlaceId place,
            final @Nullable Family family,
            final Participant... who) {
        return new EventContent(
                EventType.BIRTH,
                null,
                null,
                place,
                family == null ? null : family.id(),
                null,
                List.of(who));
    }

    private Person person(final String given) {
        return this.people.createPerson(this.editor, named(given));
    }

    private List<ChangeEvent> history(final EntityType type, final Object id) {
        return this.tree.changes().history(type, id.toString());
    }

    private ChangeEventId first(final EntityType type, final Object id) {
        return this.history(type, id).getFirst().id();
    }

    @Test
    void anExistingPersonGoesBackToAnEarlierState() {
        final Person anna = this.person("Anna");
        final Person renamed =
                this.people.replacePerson(this.editor, anna.id(), anna.version(), named("Anne"));
        final ChangeEventId creation = this.first(EntityType.PERSON, anna.id());

        assertThatThrownBy(() ->
                        this.people.restorePerson(this.editor, anna.id(), creation, null, false))
                .isInstanceOf(VersionRequiredException.class);
        assertThatThrownBy(() ->
                        this.people.restorePerson(this.editor, anna.id(), creation, 9L, false))
                .isInstanceOf(ConcurrentModificationException.class);
        final Person restored =
                this.people.restorePerson(
                        this.editor, anna.id(), creation, renamed.version(), false);

        assertThat(restored.names()).isEqualTo(anna.names());
        assertThat(restored.version()).isEqualTo(renamed.version() + 1);
        final List<ChangeEvent> history = this.history(EntityType.PERSON, anna.id());
        assertThat(history.getLast().kind()).isEqualTo(ChangeKind.RESTORED);
        // Replaying the history gives the current record.
        assertThat(PersonFields.INSTANCE.fromFields(anna.id().toString(), Replay.current(history)))
                .isEqualTo(
                        new Person(
                                restored.id(),
                                restored.names(),
                                restored.sex(),
                                restored.notes(),
                                0));
        // Restoring the state it already has changes nothing.
        assertThat(
                        this.people.restorePerson(
                                this.editor, anna.id(), creation, restored.version(), false))
                .isEqualTo(restored);
        assertThat(this.history(EntityType.PERSON, anna.id())).hasSameSizeAs(history);
    }

    @Test
    void aDeletedPersonComesBackWithTheirLinks() {
        final Person mother = this.person("Anna");
        final Person child = this.person("Karl");
        final Family family =
                this.families.createFamily(
                        this.editor,
                        new FamilyContent(
                                List.of(mother.id()),
                                RelationshipType.MARRIED,
                                List.of(new Child(child.id(), ChildRelation.ADOPTED))));
        final Event birth =
                this.events.createEvent(
                        this.editor,
                        event(
                                null,
                                family,
                                new Participant(child.id(), ParticipantRole.PRINCIPAL),
                                new Participant(mother.id(), ParticipantRole.WITNESS)));
        this.people.deletePerson(this.editor, child.id(), child.version(), true);
        this.people.deletePerson(this.editor, mother.id(), mother.version(), true);

        this.people.restorePerson(
                this.editor, child.id(), this.first(EntityType.PERSON, child.id()), null, false);
        final Person back =
                this.people.restorePerson(
                        this.editor,
                        mother.id(),
                        this.first(EntityType.PERSON, mother.id()),
                        null,
                        false);

        assertThat(back.names()).isEqualTo(mother.names());
        final Family relinked = this.tree.families().find(family.id()).orElseThrow();
        assertThat(relinked.partners()).containsExactly(mother.id());
        assertThat(relinked.children())
                .containsExactly(new Child(child.id(), ChildRelation.ADOPTED));
        assertThat(this.tree.events().find(birth.id()).orElseThrow().participants())
                .containsExactlyInAnyOrder(
                        new Participant(child.id(), ParticipantRole.PRINCIPAL),
                        new Participant(mother.id(), ParticipantRole.WITNESS));
        // The restore and the relinking are one action.
        final ChangeEvent restore = this.history(EntityType.PERSON, mother.id()).getLast();
        assertThat(restore.kind()).isEqualTo(ChangeKind.RESTORED);
        assertThat(this.history(EntityType.FAMILY, family.id()).getLast().correlationId())
                .isEqualTo(restore.correlationId());
    }

    @Test
    void linksThatCantComeBackNeedConfirmation() {
        final Person mother = this.person("Anna");
        final Family family =
                this.families.createFamily(
                        this.editor,
                        new FamilyContent(
                                List.of(mother.id()), RelationshipType.MARRIED, List.of()));
        final Event birth =
                this.events.createEvent(
                        this.editor,
                        event(null, null, new Participant(mother.id(), ParticipantRole.OTHER)));
        this.people.deletePerson(this.editor, mother.id(), mother.version(), true);
        // The family takes two other partners, and the event is deleted.
        final Family full = this.tree.families().find(family.id()).orElseThrow();
        this.families.replaceFamily(
                this.editor,
                family.id(),
                full.version(),
                new FamilyContent(
                        List.of(this.person("Eva").id(), this.person("Hans").id()),
                        RelationshipType.MARRIED,
                        List.of()));
        this.events.deleteEvent(
                this.editor,
                birth.id(),
                this.tree.events().find(birth.id()).orElseThrow().version());
        final ChangeEventId creation = this.first(EntityType.PERSON, mother.id());

        assertThatThrownBy(() ->
                        this.people.restorePerson(this.editor, mother.id(), creation, null, false))
                .isInstanceOfSatisfying(
                        RestoreConflictException.class,
                        e ->
                                assertThat(e.links())
                                        .containsExactly(
                                                new Link(
                                                        EntityType.FAMILY,
                                                        family.id().toString(),
                                                        "partners"),
                                                new Link(
                                                        EntityType.EVENT,
                                                        birth.id().toString(),
                                                        "participants")));
        assertThat(this.tree.people().find(mother.id())).isEmpty();
        this.people.restorePerson(this.editor, mother.id(), creation, null, true);

        assertThat(this.tree.people().find(mother.id())).isPresent();
        assertThat(this.tree.families().find(family.id()).orElseThrow().partners())
                .doesNotContain(mother.id());
    }

    @Test
    void aDeletedPlaceComesBackWithItsPlacesAndEvents() {
        final Place country =
                this.places.createPlace(
                        this.editor, new PlaceContent("Land", PlaceType.COUNTRY, null, null));
        final Place county =
                this.places.createPlace(
                        this.editor,
                        new PlaceContent("Kreis", PlaceType.COUNTY, null, country.id()));
        final Place town =
                this.places.createPlace(
                        this.editor, new PlaceContent("Stadt", PlaceType.TOWN, null, county.id()));
        final Event birth = this.events.createEvent(this.editor, event(county.id(), null));
        this.places.deletePlace(this.editor, county.id(), county.version(), true);
        this.places.deletePlace(this.editor, country.id(), country.version(), true);
        final ChangeEventId creation = this.first(EntityType.PLACE, county.id());

        // Its country is gone too, so it can't lie there any more.
        assertThatThrownBy(() ->
                        this.places.restorePlace(this.editor, county.id(), creation, null, false))
                .isInstanceOfSatisfying(
                        RestoreConflictException.class,
                        e ->
                                assertThat(e.links())
                                        .containsExactly(
                                                new Link(
                                                        EntityType.PLACE,
                                                        country.id().toString(),
                                                        "enclosedBy")));
        final Place back = this.places.restorePlace(this.editor, county.id(), creation, null, true);

        assertThat(back.enclosedBy()).isNull();
        assertThat(this.tree.places().find(town.id()).orElseThrow().enclosedBy())
                .isEqualTo(county.id());
        assertThat(this.tree.events().find(birth.id()).orElseThrow().place())
                .isEqualTo(county.id());
    }

    @Test
    void aDeletedFamilyComesBackWithItsEventsButWithoutDeletedMembers() {
        final Person mother = this.person("Anna");
        final Person father = this.person("Hans");
        final Family family =
                this.families.createFamily(
                        this.editor,
                        new FamilyContent(
                                List.of(mother.id(), father.id()),
                                RelationshipType.MARRIED,
                                List.of()));
        final Event wedding = this.events.createEvent(this.editor, event(null, family));
        this.families.deleteFamily(this.editor, family.id(), family.version(), true);
        this.people.deletePerson(this.editor, father.id(), father.version(), false);
        final ChangeEventId creation = this.first(EntityType.FAMILY, family.id());

        assertThatThrownBy(() ->
                        this.families.restoreFamily(
                                this.editor, family.id(), creation, null, false))
                .isInstanceOf(RestoreConflictException.class);
        final Family back =
                this.families.restoreFamily(this.editor, family.id(), creation, null, true);

        assertThat(back.partners()).containsExactly(mother.id());
        assertThat(this.tree.events().find(wedding.id()).orElseThrow().family())
                .isEqualTo(family.id());
    }

    @Test
    void aDeletedEventComesBackWithoutWhatIsGone() {
        final Person anna = this.person("Anna");
        final Place church =
                this.places.createPlace(
                        this.editor, new PlaceContent("Kirche", PlaceType.BUILDING, null, null));
        final Event birth =
                this.events.createEvent(
                        this.editor,
                        event(
                                church.id(),
                                null,
                                new Participant(anna.id(), ParticipantRole.PRINCIPAL)));
        this.events.deleteEvent(this.editor, birth.id(), birth.version());
        this.people.deletePerson(this.editor, anna.id(), anna.version(), false);
        final ChangeEventId creation = this.first(EntityType.EVENT, birth.id());

        assertThatThrownBy(() ->
                        this.events.restoreEvent(this.editor, birth.id(), creation, null, false))
                .isInstanceOfSatisfying(
                        RestoreConflictException.class,
                        e ->
                                assertThat(e.links())
                                        .containsExactly(
                                                new Link(
                                                        EntityType.PERSON,
                                                        anna.id().toString(),
                                                        "participants")));
        final Event back = this.events.restoreEvent(this.editor, birth.id(), creation, null, true);

        assertThat(back.participants()).isEmpty();
        assertThat(back.place()).isEqualTo(church.id());
        assertThat(back.version()).isZero();
    }

    @Test
    void checksAccessAndTheChange() {
        final Person anna = this.person("Anna");
        this.people.deletePerson(this.editor, anna.id(), anna.version(), false);
        final List<ChangeEvent> history = this.history(EntityType.PERSON, anna.id());
        final UserId viewer = this.tree.user(DefaultRoles.VIEWER);

        assertThatThrownBy(() ->
                        this.people.restorePerson(
                                viewer, anna.id(), history.getFirst().id(), null, false))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.people.restorePerson(
                                this.editor, anna.id(), history.getLast().id(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        this.people.restorePerson(
                                this.editor, anna.id(), ChangeEventId.newId(), null, false))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() ->
                        this.people.restorePerson(
                                this.editor,
                                PersonId.newId(),
                                history.getFirst().id(),
                                null,
                                false))
                .isInstanceOf(NotFoundException.class);
        // A contributor may create, so they may bring back a deleted person.
        final UserId contributor = this.tree.user(DefaultRoles.CONTRIBUTOR);
        assertThat(
                        this.people.restorePerson(
                                contributor, anna.id(), history.getFirst().id(), null, false))
                .isNotNull();
    }
}
