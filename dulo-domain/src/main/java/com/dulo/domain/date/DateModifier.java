package com.dulo.domain.date;

import com.dulo.domain.shared.Keys;

import java.util.Optional;

/** How exact a date is. */
public enum DateModifier {
    /** Around the date. */
    ABOUT,
    /** After the date. */
    AFTER,
    /** Before the date. */
    BEFORE,
    /** Between the start and the end date. */
    BETWEEN,
    /** Estimated from other facts, such as an age at death. */
    ESTIMATED,
    /** On the date, as precise as it is given. */
    EXACT,
    ;

    /** The modifier with the given {@link #key()}, ignoring case. */
    public static Optional<DateModifier> fromKey(final String key) {
        return Keys.fromKey(DateModifier.class, key);
    }

    /** Stable external name, e.g. {@code about}. */
    public String key() {
        return Keys.key(this);
    }
}
