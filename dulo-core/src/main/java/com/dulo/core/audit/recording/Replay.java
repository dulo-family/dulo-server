package com.dulo.core.audit.recording;

import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.shared.NotFoundException;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Rebuilds the state of a record from its events. The events are complete, so applying every change
 * in order gives the fields the record had after any of them.
 */
public final class Replay {

    private Replay() {}

    /**
     * The fields a record had right after one of its events.
     *
     * @param history the record's events, oldest first
     * @return the fields with a value
     * @throws NotFoundException if the event is not in the history
     * @throws IllegalArgumentException if the event is a deletion, after which the record had no
     *     fields
     */
    public static Map<String, Object> stateAfter(
            final List<ChangeEvent> history, final ChangeEventId eventId) {
        final Map<String, Object> fields = new LinkedHashMap<>();
        for (final ChangeEvent event : history) {
            if (event.kind() == ChangeKind.DELETED) {
                fields.clear();
            } else {
                for (final FieldChange change : event.changes()) {
                    final Object after = change.after();
                    if (after == null) {
                        fields.remove(change.field());
                    } else {
                        fields.put(change.field(), after);
                    }
                }
            }
            if (event.id().equals(eventId)) {
                if (event.kind() == ChangeKind.DELETED) {
                    throw new IllegalArgumentException(
                            "The record didn't exist after its deletion; choose an earlier change");
                }
                return fields;
            }
        }
        throw new NotFoundException("Change", eventId);
    }

    /** The current fields of a record: its state after its last event, empty if it is deleted. */
    public static Map<String, Object> current(final List<ChangeEvent> history) {
        return history.isEmpty() || history.getLast().kind() == ChangeKind.DELETED
                ? Map.of()
                : stateAfter(history, history.getLast().id());
    }

    /** The record's last deletion, if the record is deleted now. */
    public static @Nullable ChangeEvent lastDeletion(final List<ChangeEvent> history) {
        return history.isEmpty() || history.getLast().kind() != ChangeKind.DELETED
                ? null
                : history.getLast();
    }

    /**
     * The entry a list field of another record lost in an action, such as the child entry {@code
     * {person, relation}} a family lost when the person was deleted.
     *
     * @param otherHistory the other record's events
     * @param correlationId the action
     * @param field the list field
     * @param person the person id the entry names in its {@code person} key
     * @return the entry, or null if the history has none
     */
    public static @Nullable Map<?, ?> removedEntry(
            final List<ChangeEvent> otherHistory,
            final UUID correlationId,
            final String field,
            final String person) {
        return otherHistory.stream()
                .filter(event -> event.correlationId().equals(correlationId))
                .flatMap(event -> event.changes().stream())
                .filter(change -> change.field().equals(field))
                .map(FieldChange::before)
                .filter(List.class::isInstance)
                .flatMap(before -> ((List<?>) before).stream())
                .filter(Map.class::isInstance)
                .map(entry -> (Map<?, ?>) entry)
                .filter(entry -> Objects.equals(entry.get("person"), person))
                .findFirst()
                .orElse(null);
    }
}
