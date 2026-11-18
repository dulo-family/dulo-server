package com.dulo.domain.event;

import com.dulo.domain.shared.Keys;

import java.util.Optional;

/** The part a person had in an event. */
public enum ParticipantRole {
    GODPARENT,
    /** The priest, registrar or other official who conducted it. */
    OFFICIANT,
    OTHER,
    /** Whom the event is about, such as the person born or the couple married. */
    PRINCIPAL,
    WITNESS,
    ;

    /** The role with the given {@link #key()}, ignoring case. */
    public static Optional<ParticipantRole> fromKey(final String key) {
        return Keys.fromKey(ParticipantRole.class, key);
    }

    /** Stable external name, e.g. {@code principal}. */
    public String key() {
        return Keys.key(this);
    }
}
