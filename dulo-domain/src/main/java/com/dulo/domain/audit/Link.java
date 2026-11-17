package com.dulo.domain.audit;

import java.util.Objects;

/**
 * A reference from one record to another, such as an event naming its place.
 *
 * @param entityType the type of the record that holds the reference
 * @param entityId the id of the record that holds the reference
 * @param field the field of that record that holds the reference
 */
public record Link(EntityType entityType, String entityId, String field) {

    public Link {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(field, "field");
    }
}
