package com.dulo.core.place.port.out;

import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.shared.PagePosition;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Stores the places of the tree. */
public interface PlaceRepository {

    Optional<Place> find(PlaceId id);

    /**
     * Places sorted by {@link Place#sortName()}, then by id.
     *
     * @param search only places whose sort name contains this lower-case text, or null for all
     * @param after only places after this position, or null to start with the first
     * @param limit the maximum number of places
     */
    List<Place> find(@Nullable String search, @Nullable PagePosition after, int limit);

    /** The places that lie directly in the place, sorted like {@link #find}. */
    List<Place> findEnclosedBy(PlaceId id);

    /**
     * Inserts a new place or updates an existing one.
     *
     * @return the stored place with its new version
     * @throws com.dulo.domain.shared.ConcurrentModificationException if {@code place.version()} is
     *     stale
     */
    Place save(Place place);

    /**
     * @throws com.dulo.domain.shared.ConcurrentModificationException if {@code place.version()} is
     *     stale
     */
    void delete(Place place);
}
