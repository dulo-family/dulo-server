package com.dulo.domain.audit;

import com.dulo.domain.shared.Ids;

import java.util.Objects;
import java.util.UUID;

public record ChangeEventId(UUID value) {

    public ChangeEventId {
        Objects.requireNonNull(value, "value");
    }

    public static ChangeEventId newId() {
        return new ChangeEventId(Ids.newId());
    }

    /**
     * @throws IllegalArgumentException if the value is not a UUID
     */
    public static ChangeEventId parse(final String value) {
        return new ChangeEventId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
