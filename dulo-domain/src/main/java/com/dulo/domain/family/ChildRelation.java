package com.dulo.domain.family;

import com.dulo.domain.shared.Keys;

import java.util.Optional;

/** How a child belongs to a family. */
public enum ChildRelation {
    ADOPTED,
    BIRTH,
    FOSTER,
    /** The child of one partner only. */
    STEP,
    ;

    /** The relation with the given {@link #key()}, ignoring case. */
    public static Optional<ChildRelation> fromKey(final String key) {
        return Keys.fromKey(ChildRelation.class, key);
    }

    /** Stable external name, e.g. {@code adopted}. */
    public String key() {
        return Keys.key(this);
    }
}
