package com.dulo.adapter.rest.event;

import com.dulo.core.event.port.in.ManageEventsUseCase.EventContent;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.place.PlaceId;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Everything about an event; a change replaces all of it. */
record EventRequest(
        @Schema(description = EventTypes.DESCRIPTION, example = "birth") String type,
        @Schema(description = "What happened, for the type `custom` only.")
                @Nullable String customType,
        @Nullable DateBody date,
        @Schema(description = "The id of the place where it happened.") @Nullable String place,
        @Schema(description = "The id of the family it belongs to, such as for a marriage.")
                @Nullable String family,
        @Nullable String description,
        @Schema(description = "The people who took part, each once; may be empty.")
                List<ParticipantBody> participants) {

    /**
     * @throws IllegalArgumentException for missing or invalid values
     */
    EventContent toContent() {
        if (this.type == null || this.participants == null) {
            throw new IllegalArgumentException("'type' and 'participants' are required");
        }
        return new EventContent(
                EventTypes.parse(this.type),
                this.customType,
                this.date == null ? null : this.date.toDate(),
                this.place == null ? null : PlaceId.parse(this.place),
                this.family == null ? null : FamilyId.parse(this.family),
                this.description,
                this.participants.stream().map(ParticipantBody::toParticipant).toList());
    }
}
