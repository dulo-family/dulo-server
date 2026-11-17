package com.dulo.core.testing;

import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventQuery;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.EntityType;

import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Keeps the audit history in a list, filtered like the real adapter. */
public final class InMemoryChangeEvents implements ChangeEventRepository {

    private final List<ChangeEvent> events = new ArrayList<>();

    /** A recorder that appends here, at a fixed time. */
    public ChangeRecorder recorder() {
        return new ChangeRecorder(
                this, Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC));
    }

    @Override
    public ChangeEvent append(final ChangeEvent event) {
        final ChangeEvent stored = event.withSequence(this.events.size() + 1L);
        this.events.add(stored);
        return stored;
    }

    @Override
    public List<ChangeEvent> history(final EntityType entityType, final String entityId) {
        return this.events.stream()
                .filter(e -> e.entityType() == entityType && e.entityId().equals(entityId))
                .toList();
    }

    @Override
    public List<ChangeEvent> find(
            final ChangeEventQuery query, final @Nullable Long beforeSequence, final int limit) {
        return this.events.stream()
                .filter(e -> query.entityTypes().contains(e.entityType()))
                .filter(e -> query.entityId() == null || query.entityId().equals(e.entityId()))
                .filter(e -> query.actor() == null || query.actor().equals(e.actor()))
                .filter(e -> query.from() == null || !e.at().isBefore(query.from()))
                .filter(e -> query.to() == null || e.at().isBefore(query.to()))
                .filter(e -> beforeSequence == null || e.sequence() < beforeSequence)
                .sorted(Comparator.comparingLong(ChangeEvent::sequence).reversed())
                .limit(limit)
                .toList();
    }

    /** All events, oldest first. */
    public List<ChangeEvent> all() {
        return List.copyOf(this.events);
    }

    /** The kinds of all events of one type, oldest first. */
    public List<ChangeKind> kinds(final EntityType entityType) {
        return this.events.stream()
                .filter(e -> e.entityType() == entityType)
                .map(ChangeEvent::kind)
                .toList();
    }
}
