package com.dulo.domain.person;

import com.dulo.domain.shared.Keys;

import java.util.Optional;

/** A person's sex, as records give it. */
public enum Sex {
    FEMALE,
    MALE,
    /** Known, but neither female nor male. */
    OTHER,
    UNKNOWN,
    ;

    /** The sex with the given {@link #key()}, ignoring case. */
    public static Optional<Sex> fromKey(final String key) {
        return Keys.fromKey(Sex.class, key);
    }

    /** Stable external name, e.g. {@code female}. */
    public String key() {
        return Keys.key(this);
    }
}
