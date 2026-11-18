package com.dulo.core.place.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.audit.recording.ChangeSet;
import com.dulo.core.audit.recording.Replay;
import com.dulo.core.event.port.out.EventRepository;
import com.dulo.core.event.service.EventFields;
import com.dulo.core.place.port.in.ManagePlacesUseCase;
import com.dulo.core.place.port.out.PlaceRepository;
import com.dulo.core.shared.port.in.HasLinksException;
import com.dulo.core.shared.port.in.RestoreConflictException;
import com.dulo.core.shared.port.in.VersionRequiredException;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.shared.service.Cursors;
import com.dulo.core.shared.service.Relink;
import com.dulo.core.shared.service.Searches;
import com.dulo.domain.access.Access;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.Link;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.shared.PagePosition;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PlaceService implements ManagePlacesUseCase {

    private static final String PLACE = "place";

    private final PlaceRepository places;
    private final EventRepository eventRecords;
    private final ChangeEventRepository events;
    private final AccessPolicy accessPolicy;
    private final ChangeRecorder recorder;
    private final UnitOfWork unitOfWork;

    public PlaceService(
            final PlaceRepository places,
            final EventRepository eventRecords,
            final ChangeEventRepository events,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        this.places = places;
        this.eventRecords = eventRecords;
        this.events = events;
        this.accessPolicy = accessPolicy;
        this.recorder = recorder;
        this.unitOfWork = unitOfWork;
    }

    private static void checkVersion(final Place place, final long expectedVersion) {
        if (place.version() != expectedVersion) {
            throw new ConcurrentModificationException(
                    "Place", place.id(), expectedVersion, place.version());
        }
    }

    private static PagePosition position(final Place place) {
        return new PagePosition(place.sortName(), place.id().value());
    }

    @Override
    public CursorPage<Place> places(
            final UserId caller,
            final @Nullable String search,
            final @Nullable String after,
            final int limit) {
        CursorPage.checkLimit(limit);
        final PagePosition position = Cursors.decodePosition(after);
        final String normalized = Searches.normalize(search);
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.PLACES, Access.VIEW);
            return Cursors.page(
                    this.places.find(normalized, position, limit + 1),
                    limit,
                    PlaceService::position);
        });
    }

    @Override
    public Place place(final UserId caller, final PlaceId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.PLACES, Access.VIEW);
            return this.find(id);
        });
    }

    @Override
    public Place createPlace(final UserId caller, final PlaceContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.PLACES, Access.CREATE);
            final Place place =
                    Place.create(
                            content.name(),
                            content.type(),
                            content.coordinates(),
                            content.enclosedBy());
            this.checkEnclosure(place);
            final Place stored = this.places.save(place);
            this.recorder.changeSet(caller).created(PlaceFields.INSTANCE, stored);
            return stored;
        });
    }

    @Override
    public Place replacePlace(
            final UserId caller,
            final PlaceId id,
            final long expectedVersion,
            final PlaceContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.PLACES, Access.EDIT);
            final Place existing = this.find(id);
            checkVersion(existing, expectedVersion);
            final Place changed =
                    new Place(
                            id,
                            content.name(),
                            content.type(),
                            content.coordinates(),
                            content.enclosedBy(),
                            existing.version());
            this.checkEnclosure(changed);
            final Place stored = this.places.save(changed);
            this.recorder.changeSet(caller).changed(PlaceFields.INSTANCE, existing, stored);
            return stored;
        });
    }

    @Override
    public void deletePlace(
            final UserId caller,
            final PlaceId id,
            final long expectedVersion,
            final boolean confirmed) {
        this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.PLACES, Access.DELETE);
            final Place place = this.find(id);
            checkVersion(place, expectedVersion);
            final List<Place> enclosed = this.places.findEnclosedBy(id);
            final List<Event> events = this.eventRecords.findAtPlace(id);
            final List<Link> links = new ArrayList<>();
            for (final Place child : enclosed) {
                links.add(
                        new Link(EntityType.PLACE, child.id().toString(), PlaceFields.ENCLOSED_BY));
            }
            for (final Event event : events) {
                links.add(new Link(EntityType.EVENT, event.id().toString(), EventFields.PLACE));
            }
            if (!links.isEmpty() && !confirmed) {
                throw new HasLinksException(PLACE, id, links);
            }
            final ChangeSet changes = this.recorder.changeSet(caller);
            for (final Place child : enclosed) {
                changes.changed(
                        PlaceFields.INSTANCE, child, this.places.save(child.withoutEnclosure()));
            }
            for (final Event event : events) {
                changes.changed(
                        EventFields.INSTANCE, event, this.eventRecords.save(event.withoutPlace()));
            }
            this.places.delete(place);
            changes.deleted(PlaceFields.INSTANCE, place, links);
            return place;
        });
    }

    @Override
    public Place restorePlace(
            final UserId caller,
            final PlaceId id,
            final ChangeEventId eventId,
            final @Nullable Long expectedVersion,
            final boolean confirmed) {
        return this.unitOfWork.write(() -> {
            final List<ChangeEvent> history = this.events.history(EntityType.PLACE, id.toString());
            if (history.isEmpty()) {
                throw new NotFoundException("Place", id);
            }
            final Optional<Place> current = this.places.find(id);
            this.accessPolicy.require(
                    caller, Feature.PLACES, current.isPresent() ? Access.EDIT : Access.CREATE);
            Place target =
                    PlaceFields.INSTANCE.fromFields(
                            id.toString(), Replay.stateAfter(history, eventId));
            if (current.isPresent()) {
                if (expectedVersion == null) {
                    throw new VersionRequiredException("place", id);
                }
                checkVersion(current.get(), expectedVersion);
            }
            final List<Link> conflicts = new ArrayList<>();
            final PlaceId enclosedBy = target.enclosedBy();
            if (enclosedBy != null && this.enclosureProblem(target) != null) {
                conflicts.add(
                        new Link(EntityType.PLACE, enclosedBy.toString(), PlaceFields.ENCLOSED_BY));
                target = target.withoutEnclosure();
            }
            final ChangeEvent deletion = Replay.lastDeletion(history);
            final List<Relink<?>> relinks =
                    deletion == null ? List.of() : this.relinks(target, deletion, conflicts);
            if (!conflicts.isEmpty() && !confirmed) {
                throw new RestoreConflictException("place", id, conflicts);
            }
            // The record is already as it was: nothing to change.
            if (current.isPresent()
                    && PlaceFields.INSTANCE
                            .fields(current.get())
                            .equals(PlaceFields.INSTANCE.fields(target))) {
                return current.get();
            }
            final Place stored =
                    this.places.save(
                            new Place(
                                    id,
                                    target.name(),
                                    target.type(),
                                    target.coordinates(),
                                    target.enclosedBy(),
                                    current.map(Place::version).orElse(0L)));
            final ChangeSet changes = this.recorder.changeSet(caller);
            changes.restored(PlaceFields.INSTANCE, current.orElse(null), stored);
            relinks.forEach(relink -> relink.apply(changes));
            return stored;
        });
    }

    @Override
    public List<ChangeEvent> placeHistory(final UserId caller, final PlaceId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.PLACES, Access.VIEW);
            final List<ChangeEvent> history = this.events.history(EntityType.PLACE, id.toString());
            if (history.isEmpty()) {
                throw new NotFoundException("Place", id);
            }
            return history;
        });
    }

    /** The enclosing place must exist, and the place must not end up lying in itself. */
    private void checkEnclosure(final Place place) {
        final String problem = this.enclosureProblem(place);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    /** Why the place can't lie where it says, or null if it can. */
    private @Nullable String enclosureProblem(final Place place) {
        final Set<PlaceId> seen = new HashSet<>();
        PlaceId current = place.enclosedBy();
        while (current != null && seen.add(current)) {
            if (current.equals(place.id())) {
                return "The place '%s' would lie in itself".formatted(place.name());
            }
            final Optional<Place> parent = this.places.find(current);
            if (parent.isEmpty()) {
                return "The enclosing place '%s' doesn't exist".formatted(current);
            }
            current = parent.get().enclosedBy();
        }
        return null;
    }

    /** The places the place lies in, directly or not. */
    private Set<PlaceId> ancestors(final Place place) {
        final Set<PlaceId> ancestors = new HashSet<>();
        PlaceId current = place.enclosedBy();
        while (current != null && ancestors.add(current)) {
            current = this.places.find(current).map(Place::enclosedBy).orElse(null);
        }
        return ancestors;
    }

    /**
     * The links the deletion removed that can come back: places that still lie nowhere, and events
     * that still have no place. The others are added to the conflicts.
     */
    private List<Relink<?>> relinks(
            final Place place, final ChangeEvent deletion, final List<Link> conflicts) {
        final List<Relink<?>> relinks = new ArrayList<>();
        final Set<PlaceId> ancestors = this.ancestors(place);
        for (final Link link : deletion.removedLinks()) {
            switch (link.entityType()) {
                case PLACE -> {
                    final Optional<Place> child = this.places.find(PlaceId.parse(link.entityId()));
                    if (child.isEmpty()
                            || (child.get().enclosedBy() != null
                                    && !place.id().equals(child.get().enclosedBy()))
                            || ancestors.contains(child.get().id())) {
                        conflicts.add(link);
                    } else if (child.get().enclosedBy() == null) {
                        relinks.add(
                                new Relink<>(
                                        PlaceFields.INSTANCE,
                                        child.get(),
                                        child.get().enclosedIn(place.id()),
                                        this.places::save));
                    }
                }
                case EVENT -> {
                    final Optional<Event> event =
                            this.eventRecords.find(EventId.parse(link.entityId()));
                    if (event.isEmpty()
                            || (event.get().place() != null
                                    && !place.id().equals(event.get().place()))) {
                        conflicts.add(link);
                    } else if (event.get().place() == null) {
                        relinks.add(
                                new Relink<>(
                                        EventFields.INSTANCE,
                                        event.get(),
                                        event.get().atPlace(place.id()),
                                        this.eventRecords::save));
                    }
                }
                default -> conflicts.add(link);
            }
        }
        return relinks;
    }

    private Place find(final PlaceId id) {
        return this.places.find(id).orElseThrow(() -> new NotFoundException("Place", id));
    }
}
