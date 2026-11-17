package com.dulo.domain.person;

import com.dulo.domain.shared.Keys;

import java.util.Optional;

/** Which of a person's names it is. */
public enum NameType {
    ALSO_KNOWN_AS,
    /** The name at birth, such as a maiden name. */
    BIRTH,
    /** A name taken at a marriage. */
    MARRIED,
    OTHER,
    /** A name taken on entering a religious order, or at a baptism. */
    RELIGIOUS,
    ;

    /** The type with the given {@link #key()}, ignoring case. */
    public static Optional<NameType> fromKey(final String key) {
        return Keys.fromKey(NameType.class, key);
    }

    /** Stable external name, e.g. {@code also-known-as}. */
    public String key() {
        return Keys.key(this);
    }
}
