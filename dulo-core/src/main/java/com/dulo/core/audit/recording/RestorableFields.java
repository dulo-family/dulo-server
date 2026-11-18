package com.dulo.core.audit.recording;

import java.util.Map;

/**
 * A record type whose earlier states can be restored: its fields can be turned back into a record.
 */
public interface RestorableFields<T> extends RecordFields<T> {

    /**
     * The record with these fields, as {@link #fields} gives them, and version 0.
     *
     * @param id the record's id, as {@link #id} gives it
     * @param fields the fields with a value; a missing field has none
     * @throws IllegalArgumentException if the fields don't make a valid record
     */
    T fromFields(String id, Map<String, Object> fields);
}
