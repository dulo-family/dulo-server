package com.dulo.adapter.persistence.jpa.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Row of the {@code event_participant} table: one person who took part in an event. */
@Embeddable
record EventParticipantRow(
        @Column(name = "person_id", length = 36, nullable = false) String personId,
        @Column(name = "participant_role", length = 20, nullable = false) String role) {}
