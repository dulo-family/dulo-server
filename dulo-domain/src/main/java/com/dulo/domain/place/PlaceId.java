package com.dulo.domain.place;

import com.dulo.domain.shared.Ids;

import java.util.Objects;
import java.util.UUID;

public record PlaceId(UUID value) {

    public PlaceId {
        Objects.requireNonNull(value, "value");
    }

    public static PlaceId newId() {
        return new PlaceId(Ids.newId());
    }

    /**
     * @throws IllegalArgumentException if the value is not a UUID
     */
    public static PlaceId parse(final String value) {
        return new PlaceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
