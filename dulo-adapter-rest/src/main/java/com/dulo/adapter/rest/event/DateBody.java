package com.dulo.adapter.rest.event;

import com.dulo.domain.date.DateModifier;
import com.dulo.domain.date.GenealogicalDate;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/**
 * A date as genealogy records give it. It has a {@code start}, and with {@code between} an {@code
 * end}; a date that can't be read is kept as {@code text} only, with the modifier {@code exact}.
 */
@Schema(name = "GenealogicalDate")
record DateBody(
        @Schema(allowableValues = {"about", "after", "before", "between", "estimated", "exact"})
                String modifier,
        @Nullable PartialDateBody start,
        @Schema(description = "For `between` only; not before the start.")
                @Nullable PartialDateBody end,
        @Schema(description = "The date as the source gives it.", example = "abt. May 1890")
                @Nullable String text) {

    static @Nullable DateBody from(final @Nullable GenealogicalDate date) {
        if (date == null) {
            return null;
        }
        return new DateBody(
                date.modifier().key(),
                PartialDateBody.from(date.start()),
                PartialDateBody.from(date.end()),
                date.text());
    }

    /**
     * @throws IllegalArgumentException for missing or invalid values
     */
    GenealogicalDate toDate() {
        if (this.modifier == null) {
            throw new IllegalArgumentException("A date needs a 'modifier'");
        }
        return new GenealogicalDate(
                DateModifier.fromKey(this.modifier)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unknown date modifier '%s'".formatted(this.modifier))),
                this.start == null ? null : this.start.toDate(),
                this.end == null ? null : this.end.toDate(),
                this.text);
    }
}
