package com.dulo.domain.audit;

import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Which change events to find. Null values don't filter.
 *
 * @param entityTypes the types of records; never empty
 * @param from only events at or after this instant
 * @param to only events before this instant
 */
public record ChangeEventQuery(
        Set<EntityType> entityTypes,
        @Nullable String entityId,
        @Nullable UserId actor,
        @Nullable Instant from,
        @Nullable Instant to) {

    public ChangeEventQuery {
        entityTypes = Set.copyOf(Objects.requireNonNull(entityTypes, "entityTypes"));
        if (entityTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one entity type is needed");
        }
    }
}
