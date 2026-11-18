package com.dulo.domain.family;

import com.dulo.domain.shared.Keys;

import java.util.Optional;

/** How the partners of a family were bound. */
public enum RelationshipType {
    CIVIL_UNION,
    MARRIED,
    /** Together without a formal union. */
    PARTNERS,
    UNKNOWN,
    ;

    /** The type with the given {@link #key()}, ignoring case. */
    public static Optional<RelationshipType> fromKey(final String key) {
        return Keys.fromKey(RelationshipType.class, key);
    }

    /** Stable external name, e.g. {@code civil-union}. */
    public String key() {
        return Keys.key(this);
    }
}
