package com.dulo.domain.family;

import com.dulo.domain.shared.Ids;

import java.util.Objects;
import java.util.UUID;

public record FamilyId(UUID value) {

    public FamilyId {
        Objects.requireNonNull(value, "value");
    }

    public static FamilyId newId() {
        return new FamilyId(Ids.newId());
    }

    /**
     * @throws IllegalArgumentException if the value is not a UUID
     */
    public static FamilyId parse(final String value) {
        return new FamilyId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
