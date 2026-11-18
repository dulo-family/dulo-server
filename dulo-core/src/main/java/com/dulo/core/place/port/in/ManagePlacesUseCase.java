package com.dulo.core.place.port.in;

import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.place.Coordinates;
import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.place.PlaceType;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Lists, reads and changes places. Needs the {@code places} feature, with the access each method
 * names.
 */
public interface ManagePlacesUseCase {

    /**
     * One page of places, sorted by name. Needs view access.
     *
     * @param search only places whose name contains this text, ignoring case; null for all
     * @param after the cursor of the previous page, or null for the first page
     * @param limit the page size, see {@link CursorPage#checkLimit(int)}
     * @throws IllegalArgumentException for an invalid cursor, limit or search
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not view places
     */
    CursorPage<Place> places(
            UserId caller, @Nullable String search, @Nullable String after, int limit);

    /**
     * Needs view access.
     *
     * @throws com.dulo.domain.shared.NotFoundException if the place doesn't exist
     */
    Place place(UserId caller, PlaceId id);

    /**
     * Needs create access.
     *
     * @throws IllegalArgumentException if the enclosing place doesn't exist
     */
    Place createPlace(UserId caller, PlaceContent content);

    /**
     * Replaces everything about the place. Needs edit access.
     *
     * @param expectedVersion the version the caller saw
     * @throws com.dulo.domain.shared.NotFoundException if the place doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws IllegalArgumentException if the enclosing place doesn't exist, or the place would lie
     *     in itself
     */
    Place replacePlace(UserId caller, PlaceId id, long expectedVersion, PlaceContent content);

    /**
     * Deletes the place. Needs delete access.
     *
     * @param confirmed whether to delete it even if other records refer to it, removing those
     *     references: places that lie in it then lie nowhere, and events there have no place
     * @throws com.dulo.domain.shared.NotFoundException if the place doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws com.dulo.core.shared.port.in.HasLinksException if other records refer to it and the
     *     deletion is not confirmed
     */
    void deletePlace(UserId caller, PlaceId id, long expectedVersion, boolean confirmed);

    /**
     * Brings the place back to its state right after one of its changes, as a new change. A deleted
     * place comes back with the links its deletion removed, where the other records still exist and
     * take them. Needs edit access, or create access for a deleted place.
     *
     * @param eventId the change whose result to restore
     * @param expectedVersion the version the caller saw; required while the place exists
     * @param confirmed whether to restore even if some links can't come back, leaving them out
     * @throws com.dulo.domain.shared.NotFoundException if the place never existed, or the change
     *     isn't one of its
     * @throws IllegalArgumentException if the change is a deletion
     * @throws com.dulo.core.shared.port.in.VersionRequiredException if the place exists and no
     *     version is given
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws com.dulo.core.shared.port.in.RestoreConflictException if some links can't come back
     *     and the restore is not confirmed
     */
    Place restorePlace(
            UserId caller,
            PlaceId id,
            ChangeEventId eventId,
            @Nullable Long expectedVersion,
            boolean confirmed);

    /**
     * Every change to the place, oldest first, also after it was deleted. Needs view access.
     *
     * @throws com.dulo.domain.shared.NotFoundException if the place never existed
     */
    List<ChangeEvent> placeHistory(UserId caller, PlaceId id);

    /**
     * Everything about a place that its users set.
     *
     * @param enclosedBy the larger place it lies in
     */
    record PlaceContent(
            String name,
            PlaceType type,
            @Nullable Coordinates coordinates,
            @Nullable PlaceId enclosedBy) {}
}
