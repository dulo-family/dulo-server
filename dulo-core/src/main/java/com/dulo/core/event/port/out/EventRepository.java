package com.dulo.core.event.port.out;

import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
import com.dulo.domain.event.EventType;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.shared.PagePosition;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Stores the events of the tree. */
public interface EventRepository {

    Optional<Event> find(EventId id);

    /**
     * Events sorted by {@link Event#sortKey()}, then by id. Null filters don't filter.
     *
     * @param participant only events the person took part in
     * @param place only events at the place
     * @param family only events of the family
     * @param type only events of the type
     * @param after only events after this position, or null to start with the first
     * @param limit the maximum number of events
     */
    List<Event> find(
            @Nullable PersonId participant,
            @Nullable PlaceId place,
            @Nullable FamilyId family,
            @Nullable EventType type,
            @Nullable PagePosition after,
            int limit);

    /** All events the person took part in, sorted like {@link #find}. */
    List<Event> findWithParticipant(PersonId participant);

    /** All events at the place, sorted like {@link #find}. */
    List<Event> findAtPlace(PlaceId place);

    /** All events of the family, sorted like {@link #find}. */
    List<Event> findOfFamily(FamilyId family);

    /**
     * Inserts a new event or updates an existing one.
     *
     * @return the stored event with its new version
     * @throws com.dulo.domain.shared.ConcurrentModificationException if {@code event.version()} is
     *     stale
     */
    Event save(Event event);

    /**
     * @throws com.dulo.domain.shared.ConcurrentModificationException if {@code event.version()} is
     *     stale
     */
    void delete(Event event);
}
