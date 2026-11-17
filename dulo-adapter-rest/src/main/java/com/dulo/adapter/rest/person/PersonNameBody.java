package com.dulo.adapter.rest.person;

import com.dulo.domain.person.NameType;
import com.dulo.domain.person.PersonName;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/** One of a person's names. A given name or a surname is required. */
@Schema(name = "PersonName")
record PersonNameBody(
        @Schema(allowableValues = {"also-known-as", "birth", "married", "other", "religious"})
                String type,
        @Schema(example = "Anna Maria") @Nullable String given,
        @Schema(example = "Müller") @Nullable String surname,
        @Schema(description = "A title or prefix, such as Dr.") @Nullable String prefix,
        @Schema(description = "A suffix, such as Jr.") @Nullable String suffix,
        @Nullable String nickname) {

    static PersonNameBody from(final PersonName name) {
        return new PersonNameBody(
                name.type().key(),
                name.given(),
                name.surname(),
                name.prefix(),
                name.suffix(),
                name.nickname());
    }

    /**
     * @throws IllegalArgumentException for a missing or unknown type, or a name without a given
     *     name and surname
     */
    PersonName toName() {
        if (this.type == null) {
            throw new IllegalArgumentException("Every name needs a 'type'");
        }
        return new PersonName(
                NameType.fromKey(this.type)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unknown name type '%s'".formatted(this.type))),
                this.given,
                this.surname,
                this.prefix,
                this.suffix,
                this.nickname);
    }
}
