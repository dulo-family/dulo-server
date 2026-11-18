package com.dulo.core.audit.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.event.service.EventFields;
import com.dulo.core.family.service.FamilyFields;
import com.dulo.core.person.service.PersonFields;
import com.dulo.core.place.service.PlaceFields;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.date.DateModifier;
import com.dulo.domain.date.GenealogicalDate;
import com.dulo.domain.date.PartialDate;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventType;
import com.dulo.domain.event.Participant;
import com.dulo.domain.event.ParticipantRole;
import com.dulo.domain.family.Child;
import com.dulo.domain.family.ChildRelation;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.family.RelationshipType;
import com.dulo.domain.person.NameType;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonName;
import com.dulo.domain.person.Sex;
import com.dulo.domain.place.Coordinates;
import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.place.PlaceType;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every restorable record survives the trip into the audit history and back. */
class RestorableFieldsTest {

    /** The fields as an event stores them: values with nothing left out but nulls. */
    private static <T> T roundTrip(final RestorableFields<T> fields, final T record) {
        final Map<String, Object> stored = new LinkedHashMap<>();
        fields.fields(record)
                .forEach((field, value) -> {
                    final @Nullable Object copy = new FieldChange(field, null, value).after();
                    if (copy != null) {
                        stored.put(field, copy);
                    }
                });
        return fields.fromFields(fields.id(record), stored);
    }

    @Test
    void people() {
        final Person person =
                Person.create(
                        List.of(
                                new PersonName(
                                        NameType.BIRTH, "Anna", "Müller", "Dr.", "Jr.", "Annie"),
                                PersonName.of(NameType.MARRIED, null, "Weber")),
                        Sex.FEMALE,
                        "Notes");

        assertThat(roundTrip(PersonFields.INSTANCE, person)).isEqualTo(person);
        assertThat(roundTrip(PersonFields.INSTANCE, Person.create(List.of(), Sex.UNKNOWN, null)))
                .isNotNull();
    }

    @Test
    void places() {
        final Place place =
                Place.create(
                        "Dorf", PlaceType.VILLAGE, new Coordinates(48, 11.25), PlaceId.newId());

        assertThat(roundTrip(PlaceFields.INSTANCE, place)).isEqualTo(place);
        final Place bare = Place.create("Land", PlaceType.COUNTRY, null, null);
        assertThat(roundTrip(PlaceFields.INSTANCE, bare)).isEqualTo(bare);
    }

    @Test
    void families() {
        final Family family =
                Family.create(
                        List.of(PersonId.newId(), PersonId.newId()),
                        RelationshipType.CIVIL_UNION,
                        List.of(new Child(PersonId.newId(), ChildRelation.STEP)));

        assertThat(roundTrip(FamilyFields.INSTANCE, family)).isEqualTo(family);
    }

    @Test
    void events() {
        final Event event =
                Event.create(
                        EventType.CUSTOM,
                        "Harvest",
                        new GenealogicalDate(
                                DateModifier.BETWEEN,
                                new PartialDate(1890, 5, null),
                                new PartialDate(1890, 6, 2),
                                "May or June"),
                        PlaceId.newId(),
                        FamilyId.newId(),
                        "Wheat",
                        List.of(new Participant(PersonId.newId(), ParticipantRole.WITNESS)));
        final Event textOnly =
                Event.create(
                        EventType.DEATH,
                        null,
                        new GenealogicalDate(DateModifier.EXACT, null, null, "long ago"),
                        null,
                        null,
                        null,
                        List.of());

        assertThat(roundTrip(EventFields.INSTANCE, event)).isEqualTo(event);
        assertThat(roundTrip(EventFields.INSTANCE, textOnly)).isEqualTo(textOnly);
    }

    @Test
    void malformedFieldsAreRejected() {
        final String id = PlaceId.newId().toString();

        assertThatThrownBy(() -> PlaceFields.INSTANCE.fromFields(id, Map.of("type", "town")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() ->
                        PlaceFields.INSTANCE.fromFields(id, Map.of("name", 5L, "type", "town")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        PlaceFields.INSTANCE.fromFields(id, Map.of("name", "X", "type", "castle")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        PersonFields.INSTANCE.fromFields(
                                id, Map.of("sex", "female", "names", List.of("not a map"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        EventFields.INSTANCE.fromFields(
                                id,
                                Map.of(
                                        "type",
                                        "birth",
                                        "date",
                                        Map.of("modifier", "exact", "start", Map.of("month", 5L)))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
