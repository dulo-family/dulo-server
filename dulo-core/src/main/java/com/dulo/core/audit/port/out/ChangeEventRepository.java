package com.dulo.core.audit.port.out;

import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventQuery;
import com.dulo.domain.audit.EntityType;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Stores the audit history. Events are only ever appended, never changed or removed. */
public interface ChangeEventRepository {

    /**
     * Appends an event to the audit history.
     *
     * @return the stored event, with its {@link ChangeEvent#sequence()}
     */
    ChangeEvent append(ChangeEvent event);

    /** The events of one record, oldest first. */
    List<ChangeEvent> history(EntityType entityType, String entityId);

    /**
     * Matching events, newest first.
     *
     * @param beforeSequence only events with a lower sequence, or null to start with the newest
     * @param limit the maximum number of events
     */
    List<ChangeEvent> find(ChangeEventQuery query, @Nullable Long beforeSequence, int limit);
}
