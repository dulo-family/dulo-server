package com.dulo.adapter.rest.family;

import com.dulo.domain.family.Family;
import com.dulo.domain.person.PersonId;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "Family")
record FamilyResponse(
        String id,
        @Schema(description = "The partners' person ids.") List<String> partners,
        @Schema(allowableValues = {"civil-union", "married", "partners", "unknown"})
                String relationshipType,
        List<ChildBody> children,
        @Schema(description = "Also the ETag; send it in If-Match to change the family.")
                long version) {

    static FamilyResponse from(final Family family) {
        return new FamilyResponse(
                family.id().toString(),
                family.partners().stream().map(PersonId::toString).toList(),
                family.relationshipType().key(),
                family.children().stream().map(ChildBody::from).toList(),
                family.version());
    }
}
