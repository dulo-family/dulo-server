package com.dulo.domain.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class PersonTest {

    @Test
    void namesDropBlankPartsAndNeedAGivenNameOrSurname() {
        final PersonName name =
                new PersonName(NameType.BIRTH, " Anna Maria ", "Müller", " ", "Jr.", "Annie");

        assertThat(name.given()).isEqualTo("Anna Maria");
        assertThat(name.prefix()).isNull();
        assertThat(name.searchText()).isEqualTo("anna maria annie müller jr.");
        assertThatThrownBy(() -> new PersonName(NameType.BIRTH, " ", null, "Dr.", null, "Bo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PersonName.of(NameType.BIRTH, "x".repeat(101), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sortsByThePrimaryName() {
        final Person anna =
                Person.create(
                        List.of(
                                PersonName.of(NameType.MARRIED, "Anna", "Schmidt"),
                                PersonName.of(NameType.BIRTH, "Anna", "Müller")),
                        Sex.FEMALE,
                        " ");

        assertThat(anna.sortName()).isEqualTo("anna, schmidt");
        assertThat(anna.notes()).isNull();
        assertThat(Person.create(List.of(), Sex.UNKNOWN, null).sortName()).isEmpty();
        assertThat(
                        Person.create(
                                        List.of(PersonName.of(NameType.BIRTH, "Hans", null)),
                                        Sex.MALE,
                                        null)
                                .sortName())
                .isEqualTo("hans, ");
        assertThat(
                        Person.create(
                                        List.of(PersonName.of(NameType.BIRTH, null, "Weber")),
                                        Sex.MALE,
                                        null)
                                .sortName())
                .isEqualTo(", weber");
    }

    @Test
    void limitsNamesAndNotes() {
        final List<PersonName> tooMany =
                Collections.nCopies(Person.MAX_NAMES + 1, PersonName.of(NameType.OTHER, "A", null));

        assertThatThrownBy(() -> Person.create(tooMany, Sex.UNKNOWN, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Person.create(List.of(), Sex.UNKNOWN, "x".repeat(10_001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Person(PersonId.newId(), List.of(), Sex.MALE, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumsHaveStableKeys() {
        assertThat(NameType.ALSO_KNOWN_AS.key()).isEqualTo("also-known-as");
        assertThat(NameType.fromKey("Also-Known-As")).contains(NameType.ALSO_KNOWN_AS);
        assertThat(Sex.fromKey("female")).contains(Sex.FEMALE);
        assertThat(Sex.fromKey("f")).isEmpty();
        final PersonId id = PersonId.newId();
        assertThat(PersonId.parse(id.toString())).isEqualTo(id);
    }
}
