package com.dulo.adapter.persistence.jpa.person;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
 * Row of the {@code person} table. Hibernate creates it through the no-argument constructor and
 * fills the fields afterwards, so fields without a value before that are {@code @Nullable} and
 * their getters check them.
 */
@Entity
@Table(name = "person")
class PersonJpaEntity {

    @Id
    @Column(length = 36)
    private @Nullable String id;

    @Column(nullable = false, length = 20)
    private @Nullable String sex;

    @Lob private @Nullable String notes;

    @Column(name = "sort_name", nullable = false, length = 210)
    private @Nullable String sortName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "person_name", joinColumns = @JoinColumn(name = "person_id"))
    @OrderColumn(name = "position")
    // Not final: JPA forbids final persistent fields, and Hibernate replaces the list on load.
    @SuppressWarnings("FieldMayBeFinal")
    private List<PersonNameRow> names = new ArrayList<>();

    @Version private @Nullable Long version;

    protected PersonJpaEntity() {}

    PersonJpaEntity(final String id) {
        this.id = id;
    }

    String getId() {
        return Objects.requireNonNull(id, "id");
    }

    String getSex() {
        return Objects.requireNonNull(sex, "sex");
    }

    void setSex(final String sex) {
        this.sex = sex;
    }

    @Nullable String getNotes() {
        return notes;
    }

    void setNotes(final @Nullable String notes) {
        this.notes = notes;
    }

    void setSortName(final String sortName) {
        this.sortName = sortName;
    }

    List<PersonNameRow> getNames() {
        return names;
    }

    void setNames(final List<PersonNameRow> names) {
        // Changes the managed list in place, so Hibernate only writes the differences.
        this.names.clear();
        this.names.addAll(names);
    }

    /** Version of a stored entity. Only a new entity that was never flushed has none. */
    long getVersion() {
        return Objects.requireNonNull(version, "version");
    }
}
