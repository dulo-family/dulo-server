package com.dulo.adapter.rest.family;

import com.dulo.domain.family.Child;
import com.dulo.domain.family.ChildRelation;
import com.dulo.domain.person.PersonId;

import io.swagger.v3.oas.annotations.media.Schema;

/** A child of a family. */
@Schema(name = "Child")
record ChildBody(
        @Schema(description = "The child's person id.") String person,
        @Schema(allowableValues = {"adopted", "birth", "foster", "step"}) String relation) {

    static ChildBody from(final Child child) {
        return new ChildBody(child.person().toString(), child.relation().key());
    }

    /**
     * @throws IllegalArgumentException for missing or invalid values
     */
    Child toChild() {
        if (this.person == null || this.relation == null) {
            throw new IllegalArgumentException("Every child needs a 'person' and a 'relation'");
        }
        return new Child(
                PersonId.parse(this.person),
                ChildRelation.fromKey(this.relation)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unknown child relation '%s'".formatted(this.relation))));
    }
}
