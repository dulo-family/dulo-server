package com.dulo.adapter.rest.person;

import com.dulo.core.person.port.in.ManagePeopleUseCase.PersonContent;
import com.dulo.domain.person.Sex;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Everything about a person; a change replaces all of it. */
record PersonRequest(
        @Schema(description = "The person's names, the primary one first; may be empty.")
                List<PersonNameBody> names,
        @Schema(allowableValues = {"female", "male", "other", "unknown"}) String sex,
        @Nullable String notes) {

    /**
     * @throws IllegalArgumentException for missing or invalid values
     */
    PersonContent toContent() {
        if (this.names == null || this.sex == null) {
            throw new IllegalArgumentException("'names' and 'sex' are required");
        }
        return new PersonContent(
                this.names.stream().map(PersonNameBody::toName).toList(),
                Sex.fromKey(this.sex)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unknown sex '%s'".formatted(this.sex))),
                this.notes);
    }
}
