package com.dulo.core.testing;

import com.dulo.core.place.port.out.PlaceRepository;
import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.PagePosition;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps places in a map, sorted, searched and versioned like the real adapter. */
public final class InMemoryPlaces implements PlaceRepository {

    private static final Comparator<Place> ORDER =
            Comparator.comparing(Place::sortName).thenComparing(p -> p.id().toString());

    private final Map<PlaceId, Place> places = new LinkedHashMap<>();

    @Override
    public Optional<Place> find(final PlaceId id) {
        return Optional.ofNullable(this.places.get(id));
    }

    @Override
    public List<Place> find(
            final @Nullable String search, final @Nullable PagePosition after, final int limit) {
        return this.places.values().stream()
                .filter(p -> search == null || p.sortName().contains(search))
                .filter(p -> after == null || Pages.isAfter(p.sortName(), p.id().value(), after))
                .sorted(ORDER)
                .limit(limit)
                .toList();
    }

    @Override
    public List<Place> findEnclosedBy(final PlaceId id) {
        return this.places.values().stream()
                .filter(p -> id.equals(p.enclosedBy()))
                .sorted(ORDER)
                .toList();
    }

    @Override
    public Place save(final Place place) {
        final long actual =
                Optional.ofNullable(this.places.get(place.id())).map(Place::version).orElse(0L);
        if (place.version() != actual) {
            throw new ConcurrentModificationException("Place", place.id(), place.version(), actual);
        }
        final boolean isNew = !this.places.containsKey(place.id());
        final Place stored =
                new Place(
                        place.id(),
                        place.name(),
                        place.type(),
                        place.coordinates(),
                        place.enclosedBy(),
                        isNew ? 0 : place.version() + 1);
        this.places.put(stored.id(), stored);
        return stored;
    }

    @Override
    public void delete(final Place place) {
        final Place stored = this.places.get(place.id());
        if (stored == null || stored.version() != place.version()) {
            throw new ConcurrentModificationException(
                    "Place", place.id(), place.version(), stored == null ? -1 : stored.version());
        }
        this.places.remove(place.id());
    }
}
