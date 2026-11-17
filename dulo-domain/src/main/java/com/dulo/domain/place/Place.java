package com.dulo.domain.place;

import com.dulo.domain.shared.Texts;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * A place where things happened, such as a village, a parish or a cemetery.
 *
 * @param coordinates where it is, if known
 * @param enclosedBy the larger place it lies in, such as the county of a village
 * @param version optimistic-locking version, 0 for a place that has not been stored yet
 */
public record Place(
        PlaceId id,
        String name,
        PlaceType type,
        @Nullable Coordinates coordinates,
        @Nullable PlaceId enclosedBy,
        long version) {

    public static final int NAME_MAX_LENGTH = 200;

    public Place {
        Objects.requireNonNull(id, "id");
        name = Texts.required(name, NAME_MAX_LENGTH, "The place name");
        Objects.requireNonNull(type, "type");
        if (id.equals(enclosedBy)) {
            throw new IllegalArgumentException("A place can't lie in itself");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /** A new place, not stored yet. */
    public static Place create(
            final String name,
            final PlaceType type,
            final @Nullable Coordinates coordinates,
            final @Nullable PlaceId enclosedBy) {
        return new Place(PlaceId.newId(), name, type, coordinates, enclosedBy, 0);
    }

    /** The text places are sorted and searched by: the name in lower case. */
    public String sortName() {
        return name.toLowerCase(Locale.ROOT);
    }

    /** The place, no longer lying in another one. */
    public Place withoutEnclosure() {
        return new Place(id, name, type, coordinates, null, version);
    }
}
