package com.dulo.domain.event;

import com.dulo.domain.shared.Keys;

import java.util.Optional;

/** What happened. */
public enum EventType {
    BAPTISM,
    BIRTH,
    BURIAL,
    CREMATION,
    /** Anything else; the event names it in its custom type. */
    CUSTOM,
    DEATH,
    DIVORCE,
    EDUCATION,
    EMIGRATION,
    ENGAGEMENT,
    IMMIGRATION,
    MARRIAGE,
    MILITARY,
    OCCUPATION,
    RESIDENCE,
    ;

    /** The type with the given {@link #key()}, ignoring case. */
    public static Optional<EventType> fromKey(final String key) {
        return Keys.fromKey(EventType.class, key);
    }

    /** Stable external name, e.g. {@code birth}. */
    public String key() {
        return Keys.key(this);
    }
}
