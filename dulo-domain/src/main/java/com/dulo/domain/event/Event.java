package com.dulo.domain.event;

import com.dulo.domain.date.GenealogicalDate;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.shared.Texts;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Something that happened, such as a birth, a marriage or a burial.
 *
 * @param customType what happened, for the type {@link EventType#CUSTOM} only
 * @param place where it happened
 * @param family the family it belongs to, such as for a marriage or a divorce
 * @param participants the people who took part, each once
 * @param version optimistic-locking version, 0 for an event that has not been stored yet
 */
public record Event(
        EventId id,
        EventType type,
        @Nullable String customType,
        @Nullable GenealogicalDate date,
        @Nullable PlaceId place,
        @Nullable FamilyId family,
        @Nullable String description,
        List<Participant> participants,
        long version) {

    public static final int CUSTOM_TYPE_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 10_000;
    public static final int MAX_PARTICIPANTS = 100;

    /** Sorts events without a date after all dated ones. */
    public static final String UNDATED_SORT_KEY = "99999999";

    public Event {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        customType = Texts.optional(customType, CUSTOM_TYPE_MAX_LENGTH, "The custom type");
        if ((type == EventType.CUSTOM) != (customType != null)) {
            throw new IllegalArgumentException(
                    "A custom type is required for custom events, and only for them");
        }
        description = Texts.optional(description, DESCRIPTION_MAX_LENGTH, "The description");
        participants = List.copyOf(participants);
        if (participants.size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "An event has at most %d participants".formatted(MAX_PARTICIPANTS));
        }
        if (participants.stream().map(Participant::person).distinct().count()
                != participants.size()) {
            throw new IllegalArgumentException("A person takes part in an event only once");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /** A new event, not stored yet. */
    public static Event create(
            final EventType type,
            final @Nullable String customType,
            final @Nullable GenealogicalDate date,
            final @Nullable PlaceId place,
            final @Nullable FamilyId family,
            final @Nullable String description,
            final List<Participant> participants) {
        return new Event(
                EventId.newId(),
                type,
                customType,
                date,
                place,
                family,
                description,
                participants,
                0);
    }

    /** The text events are sorted by: the date as {@code yyyymmdd}; undated events last. */
    public String sortKey() {
        final Integer key = date == null ? null : date.sortKey();
        return key == null ? UNDATED_SORT_KEY : "%08d".formatted(key);
    }

    /** The event without the person among its participants. */
    public Event without(final PersonId person) {
        return new Event(
                id,
                type,
                customType,
                date,
                place,
                family,
                description,
                participants.stream().filter(p -> !p.person().equals(person)).toList(),
                version);
    }

    /**
     * The event with another participant, last.
     *
     * @throws IllegalArgumentException if the person takes part already, or the event has the most
     *     participants
     */
    public Event withParticipant(final Participant participant) {
        return new Event(
                id,
                type,
                customType,
                date,
                place,
                family,
                description,
                Stream.concat(participants.stream(), Stream.of(participant)).toList(),
                version);
    }

    /** The event at the place. */
    public Event atPlace(final PlaceId place) {
        return new Event(
                id, type, customType, date, place, family, description, participants, version);
    }

    /** The event, belonging to the family. */
    public Event ofFamily(final FamilyId family) {
        return new Event(
                id, type, customType, date, place, family, description, participants, version);
    }

    /** The event, no longer at a place. */
    public Event withoutPlace() {
        return new Event(
                id, type, customType, date, null, family, description, participants, version);
    }

    /** The event, no longer belonging to a family. */
    public Event withoutFamily() {
        return new Event(
                id, type, customType, date, place, null, description, participants, version);
    }
}
