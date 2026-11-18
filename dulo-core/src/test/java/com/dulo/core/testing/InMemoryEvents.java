package com.dulo.core.testing;

import com.dulo.core.event.port.out.EventRepository;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
import com.dulo.domain.event.EventType;
import com.dulo.domain.event.Participant;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.PagePosition;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps events in a map, sorted, filtered and versioned like the real adapter. */
public final class InMemoryEvents implements EventRepository {

    private static final Comparator<Event> ORDER =
            Comparator.comparing(Event::sortKey).thenComparing(e -> e.id().toString());

    private final Map<EventId, Event> events = new LinkedHashMap<>();

    @Override
    public Optional<Event> find(final EventId id) {
        return Optional.ofNullable(this.events.get(id));
    }

    @Override
    public List<Event> find(
            final @Nullable PersonId participant,
            final @Nullable PlaceId place,
            final @Nullable FamilyId family,
            final @Nullable EventType type,
            final @Nullable PagePosition after,
            final int limit) {
        return this.events.values().stream()
                .filter(e ->
                        participant == null
                                || e.participants().stream()
                                        .map(Participant::person)
                                        .anyMatch(participant::equals))
                .filter(e -> place == null || place.equals(e.place()))
                .filter(e -> family == null || family.equals(e.family()))
                .filter(e -> type == null || type == e.type())
                .filter(e -> after == null || Pages.isAfter(e.sortKey(), e.id().value(), after))
                .sorted(ORDER)
                .limit(limit)
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
        final long actual =
                Optional.ofNullable(this.events.get(event.id())).map(Event::version).orElse(0L);
        if (event.version() != actual) {
            throw new ConcurrentModificationException("Event", event.id(), event.version(), actual);
        }
        final boolean isNew = !this.events.containsKey(event.id());
        final Event stored =
                new Event(
                        event.id(),
                        event.type(),
                        event.customType(),
                        event.date(),
                        event.place(),
                        event.family(),
                        event.description(),
                        event.participants(),
                        isNew ? 0 : event.version() + 1);
        this.events.put(stored.id(), stored);
        return stored;
    }

    @Override
    public void delete(final Event event) {
        final Event stored = this.events.get(event.id());
        if (stored == null || stored.version() != event.version()) {
            throw new ConcurrentModificationException(
                    "Event", event.id(), event.version(), stored == null ? -1 : stored.version());
        }
        this.events.remove(event.id());
    }
}
