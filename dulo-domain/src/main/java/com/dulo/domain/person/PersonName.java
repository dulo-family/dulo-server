package com.dulo.domain.person;

import com.dulo.domain.shared.Texts;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One of a person's names. Blank parts become null; a given name or a surname is required.
 *
 * @param given the given names, such as "Anna Maria"
 * @param prefix a title or prefix, such as "Dr."
 * @param suffix a suffix, such as "Jr."
 * @param nickname what the person was called
 */
public record PersonName(
        NameType type,
        @Nullable String given,
        @Nullable String surname,
        @Nullable String prefix,
        @Nullable String suffix,
        @Nullable String nickname) {

    public static final int PART_MAX_LENGTH = 100;

    public PersonName {
        Objects.requireNonNull(type, "type");
        given = Texts.optional(given, PART_MAX_LENGTH, "The given name");
        surname = Texts.optional(surname, PART_MAX_LENGTH, "The surname");
        prefix = Texts.optional(prefix, PART_MAX_LENGTH, "The name prefix");
        suffix = Texts.optional(suffix, PART_MAX_LENGTH, "The name suffix");
        nickname = Texts.optional(nickname, PART_MAX_LENGTH, "The nickname");
        if (given == null && surname == null) {
            throw new IllegalArgumentException("A name needs a given name or a surname");
        }
    }

    /** A name with only a given name and a surname. */
    public static PersonName of(
            final NameType type, final @Nullable String given, final @Nullable String surname) {
        return new PersonName(type, given, surname, null, null, null);
    }

    /** All parts in lower case, separated by spaces: what a search looks in. */
    public String searchText() {
        return Stream.of(prefix, given, nickname, surname, suffix)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }
}
