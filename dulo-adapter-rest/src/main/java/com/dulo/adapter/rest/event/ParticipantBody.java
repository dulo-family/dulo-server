package com.dulo.adapter.rest.event;

import com.dulo.domain.event.Participant;
import com.dulo.domain.event.ParticipantRole;
import com.dulo.domain.person.PersonId;

import io.swagger.v3.oas.annotations.media.Schema;

/** A person who took part in an event. */
@Schema(name = "Participant")
record ParticipantBody(
        @Schema(description = "The person's id.") String person,
        @Schema(allowableValues = {"godparent", "officiant", "other", "principal", "witness"})
                String role) {

    static ParticipantBody from(final Participant participant) {
        return new ParticipantBody(participant.person().toString(), participant.role().key());
    }

    /**
     * @throws IllegalArgumentException for missing or invalid values
     */
    Participant toParticipant() {
        if (this.person == null || this.role == null) {
            throw new IllegalArgumentException("Every participant needs a 'person' and a 'role'");
        }
        return new Participant(
                PersonId.parse(this.person),
                ParticipantRole.fromKey(this.role)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unknown participant role '%s'".formatted(this.role))));
    }
}
