package com.dulo.core.place.service;

import com.dulo.core.audit.recording.RecordFields;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.place.Coordinates;
import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Places in the audit history. */
public final class PlaceFields implements RecordFields<Place> {

    public static final PlaceFields INSTANCE = new PlaceFields();

    /** The field that holds the enclosing place, as links name it. */
    public static final String ENCLOSED_BY = "enclosedBy";

    private PlaceFields() {}

    @Override
    public EntityType entityType() {
        return EntityType.PLACE;
    }

    @Override
    public String id(final Place place) {
        return place.id().toString();
    }

    @Override
    public long version(final Place place) {
        return place.version();
    }

    /** Coordinates are a map with {@code latitude} and {@code longitude}. */
    @Override
    public Map<String, @Nullable Object> fields(final Place place) {
        final Coordinates coordinates = place.coordinates();
        final PlaceId enclosedBy = place.enclosedBy();
        final Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        fields.put("name", place.name());
        fields.put("type", place.type().key());
        fields.put(
                "coordinates",
                coordinates == null
                        ? null
                        : Map.of(
                                "latitude", coordinates.latitude(),
                                "longitude", coordinates.longitude()));
        fields.put(ENCLOSED_BY, enclosedBy == null ? null : enclosedBy.toString());
        return fields;
    }
}
