package com.dulo.adapter.persistence.jpa.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Row of the {@code place} table. Hibernate creates it through the no-argument constructor and
 * fills the fields afterwards, so fields without a value before that are {@code @Nullable} and
 * their getters check them.
 */
@Entity
@Table(name = "place")
class PlaceJpaEntity {

    @Id
    @Column(length = 36)
    private @Nullable String id;

    @Column(nullable = false, length = 200)
    private @Nullable String name;

    @Column(name = "sort_name", nullable = false, length = 200)
    private @Nullable String sortName;

    @Column(nullable = false, length = 20)
    private @Nullable String type;

    private @Nullable Double latitude;

    private @Nullable Double longitude;

    @Column(name = "enclosed_by", length = 36)
    private @Nullable String enclosedBy;

    @Version private @Nullable Long version;

    protected PlaceJpaEntity() {}

    PlaceJpaEntity(final String id) {
        this.id = id;
    }

    String getId() {
        return Objects.requireNonNull(id, "id");
    }

    String getName() {
        return Objects.requireNonNull(name, "name");
    }

    void setName(final String name) {
        this.name = name;
    }

    void setSortName(final String sortName) {
        this.sortName = sortName;
    }

    String getType() {
        return Objects.requireNonNull(type, "type");
    }

    void setType(final String type) {
        this.type = type;
    }

    @Nullable Double getLatitude() {
        return latitude;
    }

    @Nullable Double getLongitude() {
        return longitude;
    }

    void setCoordinates(final @Nullable Double latitude, final @Nullable Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Nullable String getEnclosedBy() {
        return enclosedBy;
    }

    void setEnclosedBy(final @Nullable String enclosedBy) {
        this.enclosedBy = enclosedBy;
    }

    /** Version of a stored entity. Only a new entity that was never flushed has none. */
    long getVersion() {
        return Objects.requireNonNull(version, "version");
    }
}
