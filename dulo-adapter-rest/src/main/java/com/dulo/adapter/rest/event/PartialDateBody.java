package com.dulo.adapter.rest.event;

import com.dulo.domain.date.PartialDate;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/** A Gregorian date that may lack its day, or its day and month. */
@Schema(name = "PartialDate")
record PartialDateBody(
        @Schema(example = "1890") Integer year,
        @Schema(description = "1 to 12; absent when only the year is known.", example = "5")
                @Nullable Integer month,
        @Schema(description = "Needs a month.", example = "17") @Nullable Integer day) {

    static @Nullable PartialDateBody from(final @Nullable PartialDate date) {
        return date == null ? null : new PartialDateBody(date.year(), date.month(), date.day());
    }

    /**
     * @throws IllegalArgumentException for a missing year or an invalid date
     */
    PartialDate toDate() {
        if (this.year == null) {
            throw new IllegalArgumentException("A date needs a 'year'");
        }
        return new PartialDate(this.year, this.month, this.day);
    }
}
