package com.dulo.adapter.persistence.jpa.family;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Row of the {@code family} table. Hibernate creates it through the no-argument constructor and
 * fills the fields afterwards, so fields without a value before that are {@code @Nullable} and
 * their getters check them.
 */
@Entity
@Table(name = "family")
class FamilyJpaEntity {

    @Id
    @Column(length = 36)
    private @Nullable String id;

    @Column(name = "relationship_type", nullable = false, length = 20)
    private @Nullable String relationshipType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "family_partner", joinColumns = @JoinColumn(name = "family_id"))
    @Column(name = "person_id", length = 36, nullable = false)
    @OrderColumn(name = "position")
    // Not final: JPA forbids final persistent fields, and Hibernate replaces the list on load.
    @SuppressWarnings("FieldMayBeFinal")
    private List<String> partners = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "family_child", joinColumns = @JoinColumn(name = "family_id"))
    @OrderColumn(name = "position")
    @SuppressWarnings("FieldMayBeFinal")
    private List<FamilyChildRow> children = new ArrayList<>();

    @Version private @Nullable Long version;

    protected FamilyJpaEntity() {}

    FamilyJpaEntity(final String id) {
        this.id = id;
    }

    String getId() {
        return Objects.requireNonNull(id, "id");
    }

    String getRelationshipType() {
        return Objects.requireNonNull(relationshipType, "relationshipType");
    }

    void setRelationshipType(final String relationshipType) {
        this.relationshipType = relationshipType;
    }

    List<String> getPartners() {
        return partners;
    }

    void setPartners(final List<String> partners) {
        // Changes the managed list in place, so Hibernate only writes the differences.
        this.partners.clear();
        this.partners.addAll(partners);
    }

    List<FamilyChildRow> getChildren() {
        return children;
    }

    void setChildren(final List<FamilyChildRow> children) {
        this.children.clear();
        this.children.addAll(children);
    }

    /** Version of a stored entity. Only a new entity that was never flushed has none. */
    long getVersion() {
        return Objects.requireNonNull(version, "version");
    }
}
