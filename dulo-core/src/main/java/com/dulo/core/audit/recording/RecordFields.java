package com.dulo.core.audit.recording;

import com.dulo.domain.audit.EntityType;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * How one type of record appears in the audit history: its fields as {@link
 * com.dulo.domain.audit.FieldValues}.
 *
 * <p>Field names and value formats are stored in every event, so they are a format that must stay
 * readable. Rename or reshape a field only together with a new event format version and an upcast
 * of the old events.
 */
public interface RecordFields<T> {

    EntityType entityType();

    /** The record's id, as text. */
    String id(T record);

    long version(T record);

    /**
     * The record's audited fields, in a stable order. A null value means the field has none. Fields
     * that change without anyone changing the record, such as the time of a user's last sign-in,
     * are left out.
     */
    Map<String, @Nullable Object> fields(T record);
}
