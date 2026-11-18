package com.dulo.core.event.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.audit.recording.Replay;
import com.dulo.core.event.port.in.ManageEventsUseCase;
import com.dulo.core.event.port.out.EventRepository;
import com.dulo.core.family.port.out.FamilyRepository;
import com.dulo.core.person.port.out.PersonRepository;
import com.dulo.core.place.port.out.PlaceRepository;
import com.dulo.core.shared.port.in.RestoreConflictException;
import com.dulo.core.shared.port.in.VersionRequiredException;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.shared.service.Cursors;
import com.dulo.core.shared.service.References;
import com.dulo.domain.access.Access;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.Link;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
import com.dulo.domain.event.Participant;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.shared.PagePosition;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EventService implements ManageEventsUseCase {

    private final EventRepository eventRecords;
    private final PersonRepository people;
    private final PlaceRepository places;
    private final FamilyRepository families;
    private final ChangeEventRepository changes;
    private final AccessPolicy accessPolicy;
    private final ChangeRecorder recorder;
    private final UnitOfWork unitOfWork;

    public EventService(
            final EventRepository eventRecords,
            final PersonRepository people,
            final PlaceRepository places,
            final FamilyRepository families,
            final ChangeEventRepository changes,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        this.eventRecords = eventRecords;
        this.people = people;
        this.places = places;
        this.families = families;
        this.changes = changes;
        this.accessPolicy = accessPolicy;
        this.recorder = recorder;
        this.unitOfWork = unitOfWork;
    }

    private static void checkVersion(final Event event, final long expectedVersion) {
        if (event.version() != expectedVersion) {
            throw new ConcurrentModificationException(
                    "Event", event.id(), expectedVersion, event.version());
        }
    }

    private static PagePosition position(final Event event) {
        return new PagePosition(event.sortKey(), event.id().value());
    }

    @Override
    public CursorPage<Event> events(
            final UserId caller,
            final EventFilter filter,
            final @Nullable String after,
            final int limit) {
        CursorPage.checkLimit(limit);
        final PagePosition position = Cursors.decodePosition(after);
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.EVENTS, Access.VIEW);
            return Cursors.page(
                    this.eventRecords.find(
                            filter.participant(),
                            filter.place(),
                            filter.family(),
                            filter.type(),
                            position,
                            limit + 1),
                    limit,
                    EventService::position);
        });
    }

    @Override
    public Event event(final UserId caller, final EventId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.EVENTS, Access.VIEW);
            return this.find(id);
        });
    }

    @Override
    public Event createEvent(final UserId caller, final EventContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.EVENTS, Access.CREATE);
            final Event event =
                    Event.create(
                            content.type(),
                            content.customType(),
                            content.date(),
                            content.place(),
                            content.family(),
                            content.description(),
                            content.participants());
            this.checkReferences(event);
            final Event stored = this.eventRecords.save(event);
            this.recorder.changeSet(caller).created(EventFields.INSTANCE, stored);
            return stored;
        });
    }

    @Override
    public Event replaceEvent(
            final UserId caller,
            final EventId id,
            final long expectedVersion,
            final EventContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.EVENTS, Access.EDIT);
            final Event existing = this.find(id);
            checkVersion(existing, expectedVersion);
            final Event changed =
                    new Event(
                            id,
                            content.type(),
                            content.customType(),
                            content.date(),
                            content.place(),
                            content.family(),
                            content.description(),
                            content.participants(),
                            existing.version());
            this.checkReferences(changed);
            final Event stored = this.eventRecords.save(changed);
            this.recorder.changeSet(caller).changed(EventFields.INSTANCE, existing, stored);
            return stored;
        });
    }

    @Override
    public void deleteEvent(final UserId caller, final EventId id, final long expectedVersion) {
        this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.EVENTS, Access.DELETE);
            final Event event = this.find(id);
            checkVersion(event, expectedVersion);
            this.eventRecords.delete(event);
            this.recorder.changeSet(caller).deleted(EventFields.INSTANCE, event, List.of());
            return event;
        });
    }

    @Override
    public Event restoreEvent(
            final UserId caller,
            final EventId id,
            final ChangeEventId eventId,
            final @Nullable Long expectedVersion,
            final boolean confirmed) {
        return this.unitOfWork.write(() -> {
            final List<ChangeEvent> history = this.changes.history(EntityType.EVENT, id.toString());
            if (history.isEmpty()) {
                throw new NotFoundException("Event", id);
            }
            final Optional<Event> current = this.eventRecords.find(id);
            this.accessPolicy.require(
                    caller, Feature.EVENTS, current.isPresent() ? Access.EDIT : Access.CREATE);
            Event target =
                    EventFields.INSTANCE.fromFields(
                            id.toString(), Replay.stateAfter(history, eventId));
            if (current.isPresent()) {
                if (expectedVersion == null) {
                    throw new VersionRequiredException("event", id);
                }
                checkVersion(current.get(), expectedVersion);
            }
            final List<Link> conflicts = new ArrayList<>();
            for (final Participant participant : target.participants()) {
                final PersonId person = participant.person();
                if (this.people.find(person).isEmpty()) {
                    conflicts.add(
                            new Link(
                                    EntityType.PERSON,
                                    person.toString(),
                                    EventFields.PARTICIPANTS));
                    target = target.without(person);
                }
            }
            final PlaceId place = target.place();
            if (place != null && this.places.find(place).isEmpty()) {
                conflicts.add(new Link(EntityType.PLACE, place.toString(), EventFields.PLACE));
                target = target.withoutPlace();
            }
            final FamilyId family = target.family();
            if (family != null && this.families.find(family).isEmpty()) {
                conflicts.add(new Link(EntityType.FAMILY, family.toString(), EventFields.FAMILY));
                target = target.withoutFamily();
            }
            if (!conflicts.isEmpty() && !confirmed) {
                throw new RestoreConflictException("event", id, conflicts);
            }
            // The record is already as it was: nothing to change.
            if (current.isPresent()
                    && EventFields.INSTANCE
                            .fields(current.get())
                            .equals(EventFields.INSTANCE.fields(target))) {
                return current.get();
            }
            final Event stored =
                    this.eventRecords.save(
                            new Event(
                                    id,
                                    target.type(),
                                    target.customType(),
                                    target.date(),
                                    target.place(),
                                    target.family(),
                                    target.description(),
                                    target.participants(),
                                    current.map(Event::version).orElse(0L)));
            this.recorder
                    .changeSet(caller)
                    .restored(EventFields.INSTANCE, current.orElse(null), stored);
            return stored;
        });
    }

    @Override
    public List<ChangeEvent> eventHistory(final UserId caller, final EventId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.EVENTS, Access.VIEW);
            final List<ChangeEvent> history = this.changes.history(EntityType.EVENT, id.toString());
            if (history.isEmpty()) {
                throw new NotFoundException("Event", id);
            }
            return history;
        });
    }

    private void checkReferences(final Event event) {
        References.requireExisting(
                "person",
                event.participants().stream().map(Participant::person).toList(),
                person -> this.people.find(person).isPresent());
        final PlaceId place = event.place();
        if (place != null) {
            References.requireExisting(
                    "place", List.of(place), id -> this.places.find(id).isPresent());
        }
        final FamilyId family = event.family();
        if (family != null) {
            References.requireExisting(
                    "family", List.of(family), id -> this.families.find(id).isPresent());
        }
    }

    private Event find(final EventId id) {
        return this.eventRecords.find(id).orElseThrow(() -> new NotFoundException("Event", id));
    }
}
