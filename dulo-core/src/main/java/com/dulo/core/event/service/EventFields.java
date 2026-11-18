package com.dulo.core.event.service;

import com.dulo.core.audit.recording.RecordFields;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.date.GenealogicalDate;
import com.dulo.domain.date.PartialDate;
import com.dulo.domain.event.Event;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.place.PlaceId;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Events in the audit history. */
public final class EventFields implements RecordFields<Event> {

    public static final EventFields INSTANCE = new EventFields();

    /** The field that holds the place, as links name it. */
    public static final String PLACE = "place";

    /** The field that holds the family, as links name it. */
    public static final String FAMILY = "family";

    /** The field that holds the participants, as links name it. */
    public static final String PARTICIPANTS = "participants";

    private EventFields() {}

    /** A partial date as {@code {year, month, day}}, leaving out what is unknown. */
    private static @Nullable Map<String, Object> partialDate(final @Nullable PartialDate date) {
        if (date == null) {
            return null;
        }
        final Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("year", date.year());
        if (date.month() != null) {
            parts.put("month", date.month());
        }
        if (date.day() != null) {
            parts.put("day", date.day());
        }
        return parts;
    }

    /** A date as {@code {modifier, start, end, text}}, leaving out what is missing. */
    private static @Nullable Map<String, Object> date(final @Nullable GenealogicalDate date) {
        if (date == null) {
            return null;
        }
        final Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("modifier", date.modifier().key());
        final Map<String, Object> start = partialDate(date.start());
        if (start != null) {
            parts.put("start", start);
        }
        final Map<String, Object> end = partialDate(date.end());
        if (end != null) {
            parts.put("end", end);
        }
        if (date.text() != null) {
            parts.put("text", date.text());
        }
        return parts;
    }

    @Override
    public EntityType entityType() {
        return EntityType.EVENT;
    }

    @Override
    public String id(final Event event) {
        return event.id().toString();
    }

    @Override
    public long version(final Event event) {
        return event.version();
    }

    /** Participants are a list of {@code {person, role}}. */
    @Override
    public Map<String, @Nullable Object> fields(final Event event) {
        final PlaceId place = event.place();
        final FamilyId family = event.family();
        final Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        fields.put("type", event.type().key());
        fields.put("customType", event.customType());
        fields.put("date", date(event.date()));
        fields.put(PLACE, place == null ? null : place.toString());
        fields.put(FAMILY, family == null ? null : family.toString());
        fields.put("description", event.description());
        fields.put(
                PARTICIPANTS,
                event.participants().stream()
                        .map(participant ->
                                Map.of(
                                        "person", participant.person().toString(),
                                        "role", participant.role().key()))
                        .toList());
        return fields;
    }
}
