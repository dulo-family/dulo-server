package com.dulo.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.domain.date.DateModifier;
import com.dulo.domain.date.GenealogicalDate;
import com.dulo.domain.date.PartialDate;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.place.PlaceId;

import org.junit.jupiter.api.Test;

import java.util.List;

class EventTest {

    private final PersonId anna = PersonId.newId();

    @Test
    void sortsByDateWithUndatedEventsLast() {
        final Event birth =
                Event.create(
                        EventType.BIRTH,
                        null,
                        GenealogicalDate.exact(new PartialDate(890, 5, 1)),
                        null,
                        null,
                        null,
                        List.of());
        final Event undated =
                Event.create(EventType.DEATH, null, null, null, null, null, List.of());
        final Event onlyText =
                Event.create(
                        EventType.DEATH,
                        null,
                        new GenealogicalDate(DateModifier.EXACT, null, null, "long ago"),
                        null,
                        null,
                        null,
                        List.of());

        assertThat(birth.sortKey()).isEqualTo("08900501");
        assertThat(undated.sortKey()).isEqualTo(Event.UNDATED_SORT_KEY);
        assertThat(onlyText.sortKey()).isEqualTo(Event.UNDATED_SORT_KEY);
    }

    @Test
    void customEventsNameTheirType() {
        assertThat(
                        Event.create(
                                        EventType.CUSTOM,
                                        " Harvest ",
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of())
                                .customType())
                .isEqualTo("Harvest");
        assertThatThrownBy(() ->
                        Event.create(EventType.CUSTOM, " ", null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        Event.create(EventType.BIRTH, "Harvest", null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void participantsTakePartOnceAndCanBeRemoved() {
        final PersonId priest = PersonId.newId();
        final Event baptism =
                Event.create(
                        EventType.BAPTISM,
                        null,
                        null,
                        PlaceId.newId(),
                        FamilyId.newId(),
                        "In the old church",
                        List.of(
                                new Participant(this.anna, ParticipantRole.PRINCIPAL),
                                new Participant(priest, ParticipantRole.OFFICIANT)));

        assertThat(baptism.without(priest).participants())
                .containsExactly(new Participant(this.anna, ParticipantRole.PRINCIPAL));
        assertThat(baptism.withoutPlace().place()).isNull();
        assertThat(baptism.withoutFamily().family()).isNull();
        assertThat(baptism.withoutFamily().place()).isEqualTo(baptism.place());
        assertThatThrownBy(() ->
                        Event.create(
                                EventType.BIRTH,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(
                                        new Participant(this.anna, ParticipantRole.PRINCIPAL),
                                        new Participant(this.anna, ParticipantRole.WITNESS))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(EventType.fromKey("Marriage")).contains(EventType.MARRIAGE);
        assertThat(ParticipantRole.GODPARENT.key()).isEqualTo("godparent");
    }
}
