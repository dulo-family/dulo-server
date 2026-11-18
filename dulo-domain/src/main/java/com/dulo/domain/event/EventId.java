package com.dulo.domain.event;

import com.dulo.domain.shared.Ids;

import java.util.Objects;
import java.util.UUID;

public record EventId(UUID value) {

    public EventId {
        Objects.requireNonNull(value, "value");
    }

    public static EventId newId() {
        return new EventId(Ids.newId());
    }

    /**
     * @throws IllegalArgumentException if the value is not a UUID
     */
    public static EventId parse(final String value) {
        return new EventId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
