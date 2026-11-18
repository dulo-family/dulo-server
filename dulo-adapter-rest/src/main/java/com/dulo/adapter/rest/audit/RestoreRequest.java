package com.dulo.adapter.rest.audit;

import com.dulo.domain.audit.ChangeEventId;

import io.swagger.v3.oas.annotations.media.Schema;

/** Which earlier state of a record to bring back. */
@Schema(name = "RestoreRequest")
public record RestoreRequest(
        @Schema(
                        description =
                                "The id of a change from the record's history: the record gets"
                                        + " the state it had right after it. Not a deletion.")
                String eventId) {

    /**
     * @throws IllegalArgumentException for a missing or malformed id
     */
    public ChangeEventId toEventId() {
        if (this.eventId == null) {
            throw new IllegalArgumentException("'eventId' is required");
        }
        return ChangeEventId.parse(this.eventId);
    }
}
