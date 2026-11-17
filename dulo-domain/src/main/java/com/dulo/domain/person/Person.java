package com.dulo.domain.person;

import com.dulo.domain.shared.Texts;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A person of the family tree.
 *
 * @param names the person's names, the primary one first; none for a person whose name is unknown
 * @param notes free text about the person
 * @param version optimistic-locking version, 0 for a person that has not been stored yet
 */
public record Person(
        PersonId id, List<PersonName> names, Sex sex, @Nullable String notes, long version) {

    public static final int MAX_NAMES = 20;
    public static final int NOTES_MAX_LENGTH = 10_000;

    public Person {
        Objects.requireNonNull(id, "id");
        names = List.copyOf(names);
        if (names.size() > MAX_NAMES) {
            throw new IllegalArgumentException(
                    "A person has at most %d names".formatted(MAX_NAMES));
        }
        Objects.requireNonNull(sex, "sex");
        notes = Texts.optional(notes, NOTES_MAX_LENGTH, "The notes");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /** A new person, not stored yet. */
    public static Person create(
            final List<PersonName> names, final Sex sex, final @Nullable String notes) {
        return new Person(PersonId.newId(), names, sex, notes, 0);
    }

    /**
     * The text people are sorted by: the primary name as "given, surname" in lower case. People
     * without a name sort first, then people without a given name.
     */
    public String sortName() {
        if (names.isEmpty()) {
            return "";
        }
        final PersonName primary = names.getFirst();
        return (Objects.requireNonNullElse(primary.given(), "")
                        + ", "
                        + Objects.requireNonNullElse(primary.surname(), ""))
                .toLowerCase(Locale.ROOT);
    }
}
