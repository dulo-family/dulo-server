package com.dulo.adapter.rest.family;

import com.dulo.core.family.port.in.ManageFamiliesUseCase.FamilyContent;
import com.dulo.domain.family.RelationshipType;
import com.dulo.domain.person.PersonId;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Everything about a family; a change replaces all of it. */
record FamilyRequest(
        @Schema(description = "Up to two person ids; may be empty.") List<String> partners,
        @Schema(allowableValues = {"civil-union", "married", "partners", "unknown"})
                String relationshipType,
        @Schema(description = "In order of birth, as far as known; may be empty.")
                List<ChildBody> children) {

    /**
     * @throws IllegalArgumentException for missing or invalid values
     */
    FamilyContent toContent() {
        if (this.partners == null || this.relationshipType == null || this.children == null) {
            throw new IllegalArgumentException(
                    "'partners', 'relationshipType' and 'children' are required");
        }
        return new FamilyContent(
                this.partners.stream().map(PersonId::parse).toList(),
                RelationshipType.fromKey(this.relationshipType)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unknown relationship type '%s'"
                                                .formatted(this.relationshipType))),
                this.children.stream().map(ChildBody::toChild).toList());
    }
}
