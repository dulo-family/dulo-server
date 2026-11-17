package com.dulo.domain.audit;

import com.dulo.domain.user.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One change to one record. The events of a record are complete: folding them in {@link
 * #sequence()} order gives the record's state after any of them.
 *
 * @param sequence the position in the audit history, which orders all events; 0 for an event that
 *     has not been stored yet
 * @param at when the change happened
 * @param actor who made the change
 * @param entityId the record's id, as text, since the id types differ between records
 * @param entityVersion the record's version after the change; for a deletion, its last version
 * @param correlationId shared by the events of one action, such as a deletion and the removal of
 *     the links to the deleted record
 * @param changes the fields the change affected, by field name
 * @param removedLinks for a deletion: the references to the record that the deletion removed
 */
public record ChangeEvent(
        ChangeEventId id,
        long sequence,
        Instant at,
        UserId actor,
        EntityType entityType,
        String entityId,
        long entityVersion,
        ChangeKind kind,
        UUID correlationId,
        List<FieldChange> changes,
        List<Link> removedLinks) {

    public ChangeEvent {
        Objects.requireNonNull(id, "id");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        if (entityVersion < 0) {
            throw new IllegalArgumentException("entityVersion must not be negative");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(correlationId, "correlationId");
        changes = List.copyOf(changes);
        if (changes.stream().map(FieldChange::field).distinct().count() != changes.size()) {
            throw new IllegalArgumentException("A field may change only once per event");
        }
        removedLinks = List.copyOf(removedLinks);
    }

    /** The event as stored, at the given position of the audit history. */
    public ChangeEvent withSequence(final long sequence) {
        return new ChangeEvent(
                id,
                sequence,
                at,
                actor,
                entityType,
                entityId,
                entityVersion,
                kind,
                correlationId,
                changes,
                removedLinks);
    }
}
