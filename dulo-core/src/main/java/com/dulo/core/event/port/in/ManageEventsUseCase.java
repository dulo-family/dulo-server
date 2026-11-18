package com.dulo.core.event.port.in;

import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.date.GenealogicalDate;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
import com.dulo.domain.event.EventType;
import com.dulo.domain.event.Participant;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Lists, reads and changes events. Needs the {@code events} feature, with the access each method
 * names.
 */
public interface ManageEventsUseCase {

    /**
     * One page of events, sorted by date; undated events last. Needs view access.
     *
     * @param after the cursor of the previous page, or null for the first page
     * @param limit the page size, see {@link CursorPage#checkLimit(int)}
     * @throws IllegalArgumentException for an invalid cursor or limit
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not view events
     */
    CursorPage<Event> events(UserId caller, EventFilter filter, @Nullable String after, int limit);

    /**
     * Needs view access.
     *
     * @throws com.dulo.domain.shared.NotFoundException if the event doesn't exist
     */
    Event event(UserId caller, EventId id);

    /**
     * Needs create access.
     *
     * @throws IllegalArgumentException if a participant, the place or the family doesn't exist
     */
    Event createEvent(UserId caller, EventContent content);

    /**
     * Replaces everything about the event. Needs edit access.
     *
     * @param expectedVersion the version the caller saw
     * @throws com.dulo.domain.shared.NotFoundException if the event doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws IllegalArgumentException if a participant, the place or the family doesn't exist
     */
    Event replaceEvent(UserId caller, EventId id, long expectedVersion, EventContent content);

    /**
     * Deletes the event. Nothing refers to events, so it needs no confirmation. Needs delete
     * access.
     *
     * @throws com.dulo.domain.shared.NotFoundException if the event doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     */
    void deleteEvent(UserId caller, EventId id, long expectedVersion);

    /**
     * Every change to the event, oldest first, also after it was deleted. Needs view access.
     *
     * @throws com.dulo.domain.shared.NotFoundException if the event never existed
     */
    List<ChangeEvent> eventHistory(UserId caller, EventId id);

    /**
     * Which events to list. Null values don't filter.
     *
     * @param participant only events the person took part in
     * @param place only events at the place
     * @param family only events of the family
     * @param type only events of the type
     */
    record EventFilter(
            @Nullable PersonId participant,
            @Nullable PlaceId place,
            @Nullable FamilyId family,
            @Nullable EventType type) {

        /** No filter: all events. */
        public static EventFilter all() {
            return new EventFilter(null, null, null, null);
        }
    }

    /**
     * Everything about an event that users set.
     *
     * @param customType what happened, for custom events only
     */
    record EventContent(
            EventType type,
            @Nullable String customType,
            @Nullable GenealogicalDate date,
            @Nullable PlaceId place,
            @Nullable FamilyId family,
            @Nullable String description,
            List<Participant> participants) {

        public EventContent {
            participants = List.copyOf(participants);
        }
    }
}
