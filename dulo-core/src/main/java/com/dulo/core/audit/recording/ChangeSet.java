package com.dulo.core.audit.recording;

import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.audit.Link;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The changes of one action, from {@link ChangeRecorder#changeSet}. Each method appends one event,
 * so call it after the record is stored, with the stored record and its new version.
 */
public final class ChangeSet {

    private final ChangeEventRepository events;
    private final UserId actor;
    private final UUID correlationId;
    private final Instant at;

    ChangeSet(
            final ChangeEventRepository events,
            final UserId actor,
            final UUID correlationId,
            final Instant at) {
        this.events = events;
        this.actor = actor;
        this.correlationId = correlationId;
        this.at = at;
    }

    /** The fields whose values differ, in the order of the record's fields. */
    static List<FieldChange> diff(
            final Map<String, @Nullable Object> before, final Map<String, @Nullable Object> after) {
        final Set<String> fields = new LinkedHashSet<>(before.keySet());
        fields.addAll(after.keySet());
        final List<FieldChange> changes = new ArrayList<>();
        for (final String field : fields) {
            final FieldChange change = new FieldChange(field, before.get(field), after.get(field));
            if (!Objects.equals(change.before(), change.after())) {
                changes.add(change);
            }
        }
        return changes;
    }

    /** Records a new record, with all its fields. */
    public <T> void created(final RecordFields<T> fields, final T record) {
        this.append(
                fields,
                record,
                ChangeKind.CREATED,
                diff(Map.of(), fields.fields(record)),
                List.of());
    }

    /** Records the fields that differ. Records nothing if none does. */
    public <T> void changed(final RecordFields<T> fields, final T before, final T after) {
        final List<FieldChange> changes = diff(fields.fields(before), fields.fields(after));
        if (!changes.isEmpty()) {
            this.append(fields, after, ChangeKind.CHANGED, changes, List.of());
        }
    }

    /**
     * Records a deleted record, with all its fields as they were.
     *
     * @param removedLinks the references to the record that the deletion removed
     */
    public <T> void deleted(
            final RecordFields<T> fields, final T record, final List<Link> removedLinks) {
        this.append(
                fields,
                record,
                ChangeKind.DELETED,
                diff(fields.fields(record), Map.of()),
                removedLinks);
    }

    /**
     * Records a record brought back to an earlier state. Records nothing if nothing changes.
     *
     * @param before the record before, or null if it was deleted
     * @param after the stored record
     */
    public <T> void restored(
            final RecordFields<T> fields, final @Nullable T before, final T after) {
        final List<FieldChange> changes =
                diff(before == null ? Map.of() : fields.fields(before), fields.fields(after));
        if (!changes.isEmpty()) {
            this.append(fields, after, ChangeKind.RESTORED, changes, List.of());
        }
    }

    private <T> void append(
            final RecordFields<T> fields,
            final T record,
            final ChangeKind kind,
            final List<FieldChange> changes,
            final List<Link> removedLinks) {
        this.events.append(
                new ChangeEvent(
                        ChangeEventId.newId(),
                        0,
                        this.at,
                        this.actor,
                        fields.entityType(),
                        fields.id(record),
                        fields.version(record),
                        kind,
                        this.correlationId,
                        changes,
                        removedLinks));
    }
}
