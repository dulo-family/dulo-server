package com.dulo.adapter.rest.place;

import com.dulo.core.place.port.in.ManagePlacesUseCase.PlaceContent;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.place.PlaceType;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/** Everything about a place; a change replaces all of it. */
record PlaceRequest(
        @Schema(example = "Plovdiv") String name,
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
        @Schema(description = "The id of the larger place it lies in.")
                @Nullable String enclosedBy) {

    /**
     * @throws IllegalArgumentException for missing or invalid values
     */
    PlaceContent toContent() {
        if (this.name == null || this.type == null) {
            throw new IllegalArgumentException("'name' and 'type' are required");
        }
        return new PlaceContent(
                this.name,
                PlaceType.fromKey(this.type)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unknown place type '%s'".formatted(this.type))),
                this.coordinates == null ? null : this.coordinates.toCoordinates(),
                this.enclosedBy == null ? null : PlaceId.parse(this.enclosedBy));
    }
}
