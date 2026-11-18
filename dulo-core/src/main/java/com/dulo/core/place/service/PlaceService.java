package com.dulo.core.place.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.audit.recording.ChangeSet;
import com.dulo.core.event.port.out.EventRepository;
import com.dulo.core.event.service.EventFields;
import com.dulo.core.place.port.in.ManagePlacesUseCase;
import com.dulo.core.place.port.out.PlaceRepository;
import com.dulo.core.shared.port.in.HasLinksException;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.shared.service.Cursors;
import com.dulo.core.shared.service.Searches;
import com.dulo.domain.access.Access;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.Link;
import com.dulo.domain.event.Event;
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
        final Set<PlaceId> seen = new HashSet<>();
        PlaceId current = place.enclosedBy();
        while (current != null && seen.add(current)) {
            if (current.equals(place.id())) {
                throw new IllegalArgumentException(
                        "The place '%s' would lie in itself".formatted(place.name()));
            }
            final PlaceId parentId = current;
            current =
                    this.places
                            .find(parentId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "The enclosing place '%s' doesn't exist"
                                                    .formatted(parentId)))
                            .enclosedBy();
        }
    }

    private Place find(final PlaceId id) {
        return this.places.find(id).orElseThrow(() -> new NotFoundException("Place", id));
    }
}
