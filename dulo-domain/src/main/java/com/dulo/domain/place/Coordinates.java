package com.dulo.domain.place;

/**
 * A position on Earth, in decimal degrees (WGS 84).
 *
 * @param latitude from -90 (south) to 90 (north)
 * @param longitude from -180 (west) to 180 (east)
 */
public record Coordinates(double latitude, double longitude) {

    public Coordinates {
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new IllegalArgumentException("The latitude must be between -90 and 90");
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("The longitude must be between -180 and 180");
        }
    }
}
