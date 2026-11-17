package com.dulo.domain.audit;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One field of a record, before and after a change. Null means the field had no value, or the
 * record didn't exist.
 *
 * @param field the field name, as the record's codec names it
 * @param before the value before the change, see {@link FieldValues}
 * @param after the value after the change, see {@link FieldValues}
 */
public record FieldChange(String field, @Nullable Object before, @Nullable Object after) {

    public FieldChange {
        Objects.requireNonNull(field, "field");
        if (field.isBlank()) {
            throw new IllegalArgumentException("The field name must not be blank");
        }
        before = FieldValues.copy(before);
        after = FieldValues.copy(after);
    }
}
