package com.dulo.domain.place;

import com.dulo.domain.shared.Keys;

import java.util.Optional;

/** What kind of place it is. */
public enum PlaceType {
    ADDRESS,
    BUILDING,
    CEMETERY,
    CITY,
    COUNTRY,
    COUNTY,
    OTHER,
    PARISH,
    REGION,
    TOWN,
    VILLAGE,
    ;

    /** The type with the given {@link #key()}, ignoring case. */
    public static Optional<PlaceType> fromKey(final String key) {
        return Keys.fromKey(PlaceType.class, key);
    }

    /** Stable external name, e.g. {@code cemetery}. */
    public String key() {
        return Keys.key(this);
    }
}
