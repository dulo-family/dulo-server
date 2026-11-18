package com.dulo.domain.event;

import com.dulo.domain.person.PersonId;

import java.util.Objects;

/** A person who took part in an event, and their part in it. */
public record Participant(PersonId person, ParticipantRole role) {

    public Participant {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(role, "role");
    }
}
