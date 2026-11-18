package com.dulo.core.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.event.port.in.ManageEventsUseCase.EventContent;
import com.dulo.core.event.port.in.ManageEventsUseCase.EventFilter;
import com.dulo.core.person.port.in.ManagePeopleUseCase.PersonContent;
import com.dulo.core.place.port.in.ManagePlacesUseCase.PlaceContent;
import com.dulo.core.testing.Genealogy;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.date.DateModifier;
import com.dulo.domain.date.GenealogicalDate;
import com.dulo.domain.date.PartialDate;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
import com.dulo.domain.event.EventType;
import com.dulo.domain.event.Participant;
import com.dulo.domain.event.ParticipantRole;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.Sex;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.place.PlaceType;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class EventServiceTest {

    private final Genealogy tree = new Genealogy();
    private final EventService service = this.tree.eventService();
    private final UserId editor = this.tree.user(DefaultRoles.EDITOR);
    private final PersonId anna =
            this.tree
                    .personService()
                    .createPerson(this.editor, new PersonContent(List.of(), Sex.FEMALE, null))
                    .id();
    private final PlaceId church =
            this.tree
                    .placeService()
                    .createPlace(
                            this.editor,
                            new PlaceContent("St. Anna", PlaceType.BUILDING, null, null))
                    .id();

    private EventContent birth(final @Nullable GenealogicalDate date) {
        return new EventContent(
                EventType.BIRTH,
                null,
                date,
                this.church,
                null,
                null,
                List.of(new Participant(this.anna, ParticipantRole.PRINCIPAL)));
    }

    @Test
    void createsReadsReplacesAndDeletesEvents() {
        final GenealogicalDate about1890 =
                new GenealogicalDate(
                        DateModifier.ABOUT, new PartialDate(1890, 5, null), null, "abt May 1890");
        final Event birth = this.service.createEvent(this.editor, this.birth(about1890));

        assertThat(this.service.event(this.editor, birth.id())).isEqualTo(birth);
        assertThat(this.service.eventHistory(this.editor, birth.id()).getFirst().changes())
                .contains(
                        new FieldChange(
                                "date",
                                null,
                                Map.of(
                                        "modifier", "about",
                                        "start", Map.of("year", 1890, "month", 5),
                                        "text", "abt May 1890")),
                        new FieldChange(
                                "participants",
                                null,
                                List.of(
                                        Map.of(
                                                "person",
                                                this.anna.toString(),
                                                "role",
                                                "principal"))));
        final Event changed =
                this.service.replaceEvent(
                        this.editor,
                        birth.id(),
                        birth.version(),
                        this.birth(GenealogicalDate.exact(new PartialDate(1890, 5, 17))));
        this.service.deleteEvent(this.editor, birth.id(), changed.version());

        assertThat(this.service.eventHistory(this.editor, birth.id()))
                .extracting(ChangeEvent::kind)
                .containsExactly(ChangeKind.CREATED, ChangeKind.CHANGED, ChangeKind.DELETED);
        assertThatThrownBy(() -> this.service.event(this.editor, birth.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsByDateWithFilters() {
        final Event undated = this.service.createEvent(this.editor, this.birth(null));
        final Event late =
                this.service.createEvent(
                        this.editor, this.birth(GenealogicalDate.exact(PartialDate.ofYear(1920))));
        final Event early =
                this.service.createEvent(
                        this.editor,
                        new EventContent(
                                EventType.CUSTOM,
                                "Harvest",
                                GenealogicalDate.exact(PartialDate.ofYear(1890)),
                                null,
                                null,
                                null,
                                List.of()));

        final CursorPage<Event> first =
                this.service.events(this.editor, EventFilter.all(), null, 2);
        assertThat(first.items()).containsExactly(early, late);
        assertThat(this.service.events(this.editor, EventFilter.all(), first.next(), 2).items())
                .containsExactly(undated);
        assertThat(
                        this.service
                                .events(
                                        this.editor,
                                        new EventFilter(
                                                this.anna, this.church, null, EventType.BIRTH),
                                        null,
                                        50)
                                .items())
                .containsExactly(late, undated);
        assertThat(
                        this.service
                                .events(
                                        this.editor,
                                        new EventFilter(null, null, null, EventType.DEATH),
                                        null,
                                        50)
                                .items())
                .isEmpty();
    }

    @Test
    void referencesMustExist() {
        assertThatThrownBy(() ->
                        this.service.createEvent(
                                this.editor,
                                new EventContent(
                                        EventType.BIRTH,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of(
                                                new Participant(
                                                        PersonId.newId(),
                                                        ParticipantRole.PRINCIPAL)))))
                .hasMessageContaining("person");
        assertThatThrownBy(() ->
                        this.service.createEvent(
                                this.editor,
                                new EventContent(
                                        EventType.BIRTH,
                                        null,
                                        null,
                                        PlaceId.newId(),
                                        null,
                                        null,
                                        List.of())))
                .hasMessageContaining("place");
        assertThatThrownBy(() ->
                        this.service.createEvent(
                                this.editor,
                                new EventContent(
                                        EventType.MARRIAGE,
                                        null,
                                        null,
                                        null,
                                        FamilyId.newId(),
                                        null,
                                        List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("family");
    }

    @Test
    void checksVersionsAccessAndExistence() {
        final Event birth = this.service.createEvent(this.editor, this.birth(null));
        final UserId contributor = this.tree.user(DefaultRoles.CONTRIBUTOR);

        assertThatThrownBy(() -> this.service.deleteEvent(contributor, birth.id(), 0))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.service.replaceEvent(this.editor, birth.id(), 3, this.birth(null)))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() -> this.service.deleteEvent(this.editor, birth.id(), 3))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() -> this.service.eventHistory(this.editor, EventId.newId()))
                .isInstanceOf(NotFoundException.class);
    }
}
