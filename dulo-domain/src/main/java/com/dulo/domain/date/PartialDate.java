package com.dulo.domain.date;

import org.jspecify.annotations.Nullable;

import java.time.YearMonth;

/**
 * A date in the Gregorian calendar that may lack its day, or its day and month.
 *
 * @param year from 1 to 9999
 * @param month from 1 to 12, or null when only the year is known
 * @param day a day of the month, or null; needs a month
 */
public record PartialDate(int year, @Nullable Integer month, @Nullable Integer day) {

    public PartialDate {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("The year must be between 1 and 9999");
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("The month must be between 1 and 12");
        }
        if (day != null) {
            if (month == null) {
                throw new IllegalArgumentException("A day needs a month");
            }
            if (!YearMonth.of(year, month).isValidDay(day)) {
                throw new IllegalArgumentException(
                        "%d-%02d has no day %d".formatted(year, month, day));
            }
        }
    }

    public static PartialDate ofYear(final int year) {
        return new PartialDate(year, null, null);
    }

    /**
     * As {@code yyyymmdd}, with 0 for a missing month or day, so it sorts before the full dates.
     */
    public int sortKey() {
        return year * 10_000 + (month == null ? 0 : month * 100) + (day == null ? 0 : day);
    }
}
