package com.dulo.adapter.rest.place;

import com.dulo.domain.place.Coordinates;
import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

@Schema(name = "Place")
record PlaceResponse(
        String id,
        String name,
        @Schema(
                        allowableValues = {
                            "address",
                            "building",
                            "cemetery",
                            "city",
                            "country",
                            "county",
                            "other",
                            "parish",
                            "region",
                            "town",
                            "village"
                        })
                String type,
        @Nullable CoordinatesBody coordinates,
        @Schema(description = "The id of the larger place it lies in.") @Nullable String enclosedBy,
        @Schema(description = "Also the ETag; send it in If-Match to change the place.")
                long version) {

    static PlaceResponse from(final Place place) {
        final Coordinates coordinates = place.coordinates();
        final PlaceId enclosedBy = place.enclosedBy();
        return new PlaceResponse(
                place.id().toString(),
                place.name(),
                place.type().key(),
                coordinates == null ? null : CoordinatesBody.from(coordinates),
                enclosedBy == null ? null : enclosedBy.toString(),
                place.version());
    }
}
