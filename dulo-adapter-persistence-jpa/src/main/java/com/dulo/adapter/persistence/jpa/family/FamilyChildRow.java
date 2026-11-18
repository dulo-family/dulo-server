package com.dulo.adapter.persistence.jpa.family;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Row of the {@code family_child} table: one child of a family. */
@Embeddable
record FamilyChildRow(
        @Column(name = "person_id", length = 36, nullable = false) String personId,
        @Column(name = "relation", length = 20, nullable = false) String relation) {}
