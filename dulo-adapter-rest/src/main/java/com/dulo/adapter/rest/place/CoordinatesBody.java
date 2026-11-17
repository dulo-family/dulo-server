package com.dulo.adapter.rest.place;

import com.dulo.domain.place.Coordinates;

import io.swagger.v3.oas.annotations.media.Schema;

/** A position in decimal degrees (WGS 84). */
@Schema(name = "Coordinates")
record CoordinatesBody(
        @Schema(description = "-90 (south) to 90 (north).", example = "42.1354") Double latitude,
        @Schema(description = "-180 (west) to 180 (east).", example = "24.7453") Double longitude) {

    static CoordinatesBody from(final Coordinates coordinates) {
        return new CoordinatesBody(coordinates.latitude(), coordinates.longitude());
    }

    /**
     * @throws IllegalArgumentException for missing or out-of-range values
     */
    Coordinates toCoordinates() {
        if (this.latitude == null || this.longitude == null) {
            throw new IllegalArgumentException("Coordinates need a latitude and a longitude");
        }
        return new Coordinates(this.latitude, this.longitude);
    }
}
