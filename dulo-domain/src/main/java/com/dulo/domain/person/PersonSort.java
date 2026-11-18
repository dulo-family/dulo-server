package com.dulo.domain.person;

import com.dulo.domain.shared.Keys;

import java.util.Optional;

/** How lists of people are sorted: by the parts of their primary name. */
public enum PersonSort {
    /** Given name, then surname. */
    GIVEN,
    /** Surname, then given name. */
    SURNAME,
    ;

    /** The sort with the given {@link #key()}, ignoring case. */
    public static Optional<PersonSort> fromKey(final String key) {
        return Keys.fromKey(PersonSort.class, key);
    }

    /** Stable external name, e.g. {@code given}. */
    public String key() {
        return Keys.key(this);
    }
}
