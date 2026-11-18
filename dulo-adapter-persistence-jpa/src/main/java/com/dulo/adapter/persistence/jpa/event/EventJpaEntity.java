package com.dulo.adapter.persistence.jpa.event;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Row of the {@code event} table. Hibernate creates it through the no-argument constructor and
 * fills the fields afterwards, so fields without a value before that are {@code @Nullable} and
 * their getters check them.
 */
@Entity
@Table(name = "event")
class EventJpaEntity {

    @Id
    @Column(length = 36)
    private @Nullable String id;

    @Column(nullable = false, length = 20)
    private @Nullable String type;

    @Column(name = "custom_type", length = 100)
    private @Nullable String customType;

    /** Null when Hibernate loads an event whose date columns are all null. */
    @Embedded private @Nullable DateColumns date;

    @Column(name = "sort_key", nullable = false, length = 8)
    private @Nullable String sortKey;

    @Column(name = "place_id", length = 36)
    private @Nullable String placeId;

    @Column(name = "family_id", length = 36)
    private @Nullable String familyId;

    @Lob private @Nullable String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_participant", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    // Not final: JPA forbids final persistent fields, and Hibernate replaces the list on load.
    @SuppressWarnings("FieldMayBeFinal")
    private List<EventParticipantRow> participants = new ArrayList<>();

    @Version private @Nullable Long version;

    protected EventJpaEntity() {}

    EventJpaEntity(final String id) {
        this.id = id;
    }

    String getId() {
        return Objects.requireNonNull(id, "id");
    }

    String getType() {
        return Objects.requireNonNull(type, "type");
    }

    void setType(final String type) {
        this.type = type;
    }

    @Nullable String getCustomType() {
        return customType;
    }

    void setCustomType(final @Nullable String customType) {
        this.customType = customType;
    }

    @Nullable DateColumns getDate() {
        return date;
    }

    void setDate(final @Nullable DateColumns date) {
        this.date = date;
    }

    void setSortKey(final String sortKey) {
        this.sortKey = sortKey;
    }

    @Nullable String getPlaceId() {
        return placeId;
    }

    void setPlaceId(final @Nullable String placeId) {
        this.placeId = placeId;
    }

    @Nullable String getFamilyId() {
        return familyId;
    }

    void setFamilyId(final @Nullable String familyId) {
        this.familyId = familyId;
    }

    @Nullable String getDescription() {
        return description;
    }

    void setDescription(final @Nullable String description) {
        this.description = description;
    }

    List<EventParticipantRow> getParticipants() {
        return participants;
    }

    void setParticipants(final List<EventParticipantRow> participants) {
        // Changes the managed list in place, so Hibernate only writes the differences.
        this.participants.clear();
        this.participants.addAll(participants);
    }

    /** Version of a stored entity. Only a new entity that was never flushed has none. */
    long getVersion() {
        return Objects.requireNonNull(version, "version");
    }
}
