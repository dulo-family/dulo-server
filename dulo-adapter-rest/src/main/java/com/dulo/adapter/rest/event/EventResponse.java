package com.dulo.adapter.rest.event;

import com.dulo.domain.event.Event;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.place.PlaceId;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;

@Schema(name = "Event")
record EventResponse(
        String id,
        @Schema(description = EventTypes.DESCRIPTION) String type,
        @Nullable String customType,
        @Nullable DateBody date,
        @Schema(description = "The id of the place where it happened.") @Nullable String place,
        @Schema(description = "The id of the family it belongs to.") @Nullable String family,
        @Nullable String description,
        List<ParticipantBody> participants,
        @Schema(description = "Also the ETag; send it in If-Match to change the event.")
                long version) {

    static EventResponse from(final Event event) {
        final PlaceId place = event.place();
        final FamilyId family = event.family();
        return new EventResponse(
                event.id().toString(),
                event.type().key(),
                event.customType(),
                DateBody.from(event.date()),
                place == null ? null : place.toString(),
                family == null ? null : family.toString(),
                event.description(),
                event.participants().stream().map(ParticipantBody::from).toList(),
                event.version());
    }
}
