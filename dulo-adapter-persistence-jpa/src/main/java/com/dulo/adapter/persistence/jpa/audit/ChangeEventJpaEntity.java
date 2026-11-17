package com.dulo.adapter.persistence.jpa.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Row of the {@code change_event} table. Rows are inserted once and never changed, so there are no
 * setters and no version. Hibernate creates loaded rows through the no-argument constructor, so the
 * fields are {@code @Nullable} and their getters check them.
 */
@Entity
@Table(name = "change_event")
class ChangeEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq")
    private @Nullable Long sequence;

    @Column(name = "event_id", nullable = false, length = 36, updatable = false)
    private @Nullable String eventId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private @Nullable Instant occurredAt;

    @Column(name = "actor_id", nullable = false, length = 36, updatable = false)
    private @Nullable String actorId;

    @Column(name = "entity_type", nullable = false, length = 20, updatable = false)
    private @Nullable String entityType;

    @Column(name = "entity_id", nullable = false, length = 50, updatable = false)
    private @Nullable String entityId;

    @Column(name = "entity_version", nullable = false, updatable = false)
    private long entityVersion;

    @Column(nullable = false, length = 20, updatable = false)
    private @Nullable String kind;

    @Column(name = "correlation_id", nullable = false, length = 36, updatable = false)
    private @Nullable String correlationId;

    @Column(name = "format_version", nullable = false, updatable = false)
    private int formatVersion;

    @Lob
    @Column(nullable = false, updatable = false)
    private @Nullable String changes;

    @Lob
    @Column(name = "removed_links", nullable = false, updatable = false)
    private @Nullable String removedLinks;

    protected ChangeEventJpaEntity() {}

    ChangeEventJpaEntity(
            final String eventId,
            final Instant occurredAt,
            final String actorId,
            final String entityType,
            final String entityId,
            final long entityVersion,
            final String kind,
            final String correlationId,
            final int formatVersion,
            final String changes,
            final String removedLinks) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityVersion = entityVersion;
        this.kind = kind;
        this.correlationId = correlationId;
        this.formatVersion = formatVersion;
        this.changes = changes;
        this.removedLinks = removedLinks;
    }

    /** Sequence of a stored row. Only a new row that was never flushed has none. */
    long getSequence() {
        return Objects.requireNonNull(sequence, "sequence");
    }

    String getEventId() {
        return Objects.requireNonNull(eventId, "eventId");
    }

    Instant getOccurredAt() {
        return Objects.requireNonNull(occurredAt, "occurredAt");
    }

    String getActorId() {
        return Objects.requireNonNull(actorId, "actorId");
    }

    String getEntityType() {
        return Objects.requireNonNull(entityType, "entityType");
    }

    String getEntityId() {
        return Objects.requireNonNull(entityId, "entityId");
    }

    long getEntityVersion() {
        return entityVersion;
    }

    String getKind() {
        return Objects.requireNonNull(kind, "kind");
    }

    String getCorrelationId() {
        return Objects.requireNonNull(correlationId, "correlationId");
    }

    int getFormatVersion() {
        return formatVersion;
    }

    String getChanges() {
        return Objects.requireNonNull(changes, "changes");
    }

    String getRemovedLinks() {
        return Objects.requireNonNull(removedLinks, "removedLinks");
    }
}
