package com.dulo.domain.date;

import com.dulo.domain.shared.Texts;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A date as genealogy records give it: possibly partial, approximate, or a range.
 *
 * <ul>
 *   <li>Usually it has a {@code start}, and for {@link DateModifier#BETWEEN} also an {@code end}.
 *   <li>A date that can't be read as a date, such as "the winter after the flood", is kept as
 *       {@code text} only, with the modifier {@link DateModifier#EXACT}.
 * </ul>
 *
 * @param text the date as the source gives it, kept next to the parsed date or instead of it
 */
public record GenealogicalDate(
        DateModifier modifier,
        @Nullable PartialDate start,
        @Nullable PartialDate end,
        @Nullable String text) {

    public static final int TEXT_MAX_LENGTH = 100;

    public GenealogicalDate {
        Objects.requireNonNull(modifier, "modifier");
        text = Texts.optional(text, TEXT_MAX_LENGTH, "The date text");
        if (start == null) {
            if (text == null || end != null || modifier != DateModifier.EXACT) {
                throw new IllegalArgumentException(
                        "A date needs a start, unless it is only a text");
            }
        } else if (modifier == DateModifier.BETWEEN) {
            if (end == null) {
                throw new IllegalArgumentException("A date between two dates needs an end");
            }
            if (end.sortKey() < start.sortKey()) {
                throw new IllegalArgumentException("The end of a date can't be before its start");
            }
        } else if (end != null) {
            throw new IllegalArgumentException("Only a date between two dates has an end");
        }
    }

    /** A date on the given day, month or year. */
    public static GenealogicalDate exact(final PartialDate date) {
        return new GenealogicalDate(DateModifier.EXACT, date, null, null);
    }

    /**
     * The date for sorting, as {@code yyyymmdd} with 0 for missing parts; the start of a range.
     * Null for a date that is only text.
     */
    public @Nullable Integer sortKey() {
        return start == null ? null : start.sortKey();
    }
}
