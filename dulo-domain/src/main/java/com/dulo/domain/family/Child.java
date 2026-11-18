package com.dulo.domain.family;

import com.dulo.domain.person.PersonId;

import java.util.Objects;

/** A child of a family. */
public record Child(PersonId person, ChildRelation relation) {

    public Child {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(relation, "relation");
    }
}
