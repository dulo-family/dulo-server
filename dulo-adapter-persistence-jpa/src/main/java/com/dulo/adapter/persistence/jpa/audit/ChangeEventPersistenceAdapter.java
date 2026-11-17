package com.dulo.adapter.persistence.jpa.audit;

import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.ChangeEventQuery;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.user.UserId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ChangeEventPersistenceAdapter implements ChangeEventRepository {

    private final ChangeEventJpaRepository repository;
    private final EntityManager entityManager;

    ChangeEventPersistenceAdapter(
            final ChangeEventJpaRepository repository, final EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    private static ChangeEventJpaEntity toEntity(final ChangeEvent event) {
        return new ChangeEventJpaEntity(
                event.id().toString(),
                event.at(),
                event.actor().toString(),
                event.entityType().key(),
                event.entityId(),
                event.entityVersion(),
                event.kind().key(),
                event.correlationId().toString(),
                ChangeEventJson.FORMAT_VERSION,
                ChangeEventJson.changes(event.changes()),
                ChangeEventJson.links(event.removedLinks()));
    }

    private static ChangeEvent toDomain(final ChangeEventJpaEntity entity) {
        return new ChangeEvent(
                ChangeEventId.parse(entity.getEventId()),
                entity.getSequence(),
                entity.getOccurredAt(),
                UserId.parse(entity.getActorId()),
                ChangeEventJson.entityType(entity.getEntityType()),
                entity.getEntityId(),
                entity.getEntityVersion(),
                ChangeKind.valueOf(entity.getKind().toUpperCase(Locale.ROOT)),
                UUID.fromString(entity.getCorrelationId()),
                ChangeEventJson.changes(entity.getFormatVersion(), entity.getChanges()),
                ChangeEventJson.links(entity.getFormatVersion(), entity.getRemovedLinks()));
    }

    @Override
    public ChangeEvent append(final ChangeEvent event) {
        if (event.sequence() != 0) {
            throw new IllegalArgumentException("The event is already stored");
        }
        return event.withSequence(this.repository.saveAndFlush(toEntity(event)).getSequence());
    }

    @Override
    public List<ChangeEvent> history(final EntityType entityType, final String entityId) {
        return this.repository
                .findAllByEntityTypeAndEntityIdOrderBySequenceAsc(entityType.key(), entityId)
                .stream()
                .map(ChangeEventPersistenceAdapter::toDomain)
                .toList();
    }

    /** Builds the query from the filters that are set, so every database gets plain conditions. */
    @Override
    public List<ChangeEvent> find(
            final ChangeEventQuery query, final @Nullable Long beforeSequence, final int limit) {
        final List<String> conditions = new ArrayList<>();
        final Map<String, Object> parameters = new LinkedHashMap<>();
        conditions.add("e.entityType in :types");
        parameters.put("types", query.entityTypes().stream().map(EntityType::key).toList());
        if (query.entityId() != null) {
            conditions.add("e.entityId = :entityId");
            parameters.put("entityId", query.entityId());
        }
        if (query.actor() != null) {
            conditions.add("e.actorId = :actorId");
            parameters.put("actorId", query.actor().toString());
        }
        if (query.from() != null) {
            conditions.add("e.occurredAt >= :from");
            parameters.put("from", query.from());
        }
        if (query.to() != null) {
            conditions.add("e.occurredAt < :to");
            parameters.put("to", query.to());
        }
        if (beforeSequence != null) {
            conditions.add("e.sequence < :beforeSequence");
            parameters.put("beforeSequence", beforeSequence);
        }
        final TypedQuery<ChangeEventJpaEntity> jpql =
                this.entityManager.createQuery(
                        "select e from ChangeEventJpaEntity e where "
                                + String.join(" and ", conditions)
                                + " order by e.sequence desc",
                        ChangeEventJpaEntity.class);
        parameters.forEach(jpql::setParameter);
        return jpql.setMaxResults(limit).getResultList().stream()
                .map(ChangeEventPersistenceAdapter::toDomain)
                .toList();
    }
}
