package com.dulo.adapter.persistence.jpa.event;

import com.dulo.adapter.persistence.jpa.shared.Keyset;
import com.dulo.core.event.port.out.EventRepository;
import com.dulo.domain.date.DateModifier;
import com.dulo.domain.date.GenealogicalDate;
import com.dulo.domain.date.PartialDate;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
import com.dulo.domain.event.EventType;
import com.dulo.domain.event.Participant;
import com.dulo.domain.event.ParticipantRole;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.PagePosition;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class EventPersistenceAdapter implements EventRepository {

    private final EventJpaRepository repository;
    private final EntityManager entityManager;

    EventPersistenceAdapter(
            final EventJpaRepository repository, final EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    private static void checkVersion(final Event event, final long actual) {
        if (event.version() != actual) {
            throw new ConcurrentModificationException("Event", event.id(), event.version(), actual);
        }
    }

    private static <T> T known(
            final String what, final String key, final Function<String, Optional<T>> fromKey) {
        return fromKey.apply(key)
                .orElseThrow(
                        () -> new IllegalStateException("Unknown %s '%s'".formatted(what, key)));
    }

    private static @Nullable DateColumns columns(final @Nullable GenealogicalDate date) {
        if (date == null) {
            return null;
        }
        final PartialDate start = date.start();
        final PartialDate end = date.end();
        return new DateColumns(
                date.modifier().key(),
                start == null ? null : start.year(),
                start == null ? null : start.month(),
                start == null ? null : start.day(),
                end == null ? null : end.year(),
                end == null ? null : end.month(),
                end == null ? null : end.day(),
                date.text());
    }

    private static @Nullable PartialDate partialDate(
            final @Nullable Integer year,
            final @Nullable Integer month,
            final @Nullable Integer day) {
        return year == null ? null : new PartialDate(year, month, day);
    }

    private static @Nullable GenealogicalDate toDomain(final @Nullable DateColumns columns) {
        if (columns == null || columns.modifier() == null) {
            return null;
        }
        return new GenealogicalDate(
                known("date modifier", columns.modifier(), DateModifier::fromKey),
                partialDate(columns.startYear(), columns.startMonth(), columns.startDay()),
                partialDate(columns.endYear(), columns.endMonth(), columns.endDay()),
                columns.text());
    }

    private static Event toDomain(final EventJpaEntity entity) {
        final String placeId = entity.getPlaceId();
        final String familyId = entity.getFamilyId();
        return new Event(
                EventId.parse(entity.getId()),
                known("event type", entity.getType(), EventType::fromKey),
                entity.getCustomType(),
                toDomain(entity.getDate()),
                placeId == null ? null : PlaceId.parse(placeId),
                familyId == null ? null : FamilyId.parse(familyId),
                entity.getDescription(),
                entity.getParticipants().stream()
                        .map(row ->
                                new Participant(
                                        PersonId.parse(row.personId()),
                                        known(
                                                "participant role",
                                                row.role(),
                                                ParticipantRole::fromKey)))
                        .toList(),
                entity.getVersion());
    }

    @Override
    public Optional<Event> find(final EventId id) {
        return this.repository.findById(id.toString()).map(EventPersistenceAdapter::toDomain);
    }

    @Override
    public List<Event> find(
            final @Nullable PersonId participant,
            final @Nullable PlaceId place,
            final @Nullable FamilyId family,
            final @Nullable EventType type,
            final @Nullable PagePosition after,
            final int limit) {
        final Keyset keyset = new Keyset().after(after, "e.sortKey", "e.id");
        if (participant != null) {
            keyset.where(
                    "exists (select 1 from EventJpaEntity withParticipant join"
                            + " withParticipant.participants participant where withParticipant ="
                            + " e and participant.personId = :participant)",
                    Map.of("participant", participant.toString()));
        }
        if (place != null) {
            keyset.where("e.placeId = :place", Map.of("place", place.toString()));
        }
        if (family != null) {
            keyset.where("e.familyId = :family", Map.of("family", family.toString()));
        }
        if (type != null) {
            keyset.where("e.type = :type", Map.of("type", type.key()));
        }
        final TypedQuery<EventJpaEntity> query =
                this.entityManager.createQuery(
                        "select e from EventJpaEntity e"
                                + keyset.whereClause()
                                + " order by e.sortKey, e.id",
                        EventJpaEntity.class);
        keyset.parameters().forEach(query::setParameter);
        return query.setMaxResults(limit).getResultList().stream()
                .map(EventPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Event> findWithParticipant(final PersonId participant) {
        return this.find(participant, null, null, null, null, Integer.MAX_VALUE);
    }

    @Override
    public List<Event> findAtPlace(final PlaceId place) {
        return this.find(null, place, null, null, null, Integer.MAX_VALUE);
    }

    @Override
    public List<Event> findOfFamily(final FamilyId family) {
        return this.find(null, null, family, null, null, Integer.MAX_VALUE);
    }

    @Override
    public Event save(final Event event) {
        final String id = event.id().toString();
        final EventJpaEntity entity =
                this.repository
                        .findById(id)
                        .map(existing -> {
                            checkVersion(event, existing.getVersion());
                            return existing;
                        })
                        .orElseGet(() -> {
                            checkVersion(event, 0L);
                            return new EventJpaEntity(id);
                        });
        final PlaceId place = event.place();
        final FamilyId family = event.family();
        entity.setType(event.type().key());
        entity.setCustomType(event.customType());
        entity.setDate(columns(event.date()));
        entity.setSortKey(event.sortKey());
        entity.setPlaceId(place == null ? null : place.toString());
        entity.setFamilyId(family == null ? null : family.toString());
        entity.setDescription(event.description());
        entity.setParticipants(
                event.participants().stream()
                        .map(participant ->
                                new EventParticipantRow(
                                        participant.person().toString(), participant.role().key()))
                        .toList());
        return toDomain(this.repository.saveAndFlush(entity));
    }

    @Override
    public void delete(final Event event) {
        final EventJpaEntity entity =
                this.repository
                        .findById(event.id().toString())
                        .orElseThrow(() ->
                                new ConcurrentModificationException(
                                        "Event", event.id(), event.version(), -1));
        checkVersion(event, entity.getVersion());
        this.repository.delete(entity);
        this.repository.flush();
    }
}
