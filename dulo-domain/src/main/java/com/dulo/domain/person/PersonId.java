package com.dulo.domain.person;

import com.dulo.domain.shared.Ids;

import java.util.Objects;
import java.util.UUID;

public record PersonId(UUID value) {

    public PersonId {
        Objects.requireNonNull(value, "value");
    }

    public static PersonId newId() {
        return new PersonId(Ids.newId());
    }

    /**
     * @throws IllegalArgumentException if the value is not a UUID
     */
    public static PersonId parse(final String value) {
        return new PersonId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
