package com.dulo.adapter.rest.person;

import com.dulo.domain.person.Person;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;

@Schema(name = "Person")
record PersonResponse(
        String id,
        @Schema(description = "The person's names, the primary one first.")
                List<PersonNameBody> names,
        @Schema(allowableValues = {"female", "male", "other", "unknown"}) String sex,
        @Nullable String notes,
        @Schema(description = "Also the ETag; send it in If-Match to change the person.")
                long version) {

    static PersonResponse from(final Person person) {
        return new PersonResponse(
                person.id().toString(),
                person.names().stream().map(PersonNameBody::from).toList(),
                person.sex().key(),
                person.notes(),
                person.version());
    }
}
