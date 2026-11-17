package com.dulo.domain.shared;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list that is read in pages.
 *
 * @param next the opaque cursor that reads the following page, or null on the last page
 */
public record CursorPage<T>(List<T> items, @Nullable String next) {

    /** The page size when the caller doesn't ask for one. */
    public static final int DEFAULT_LIMIT = 50;

    /** The largest page size. */
    public static final int MAX_LIMIT = 200;

    public CursorPage {
        items = List.copyOf(items);
    }

    /**
     * @return the limit
     * @throws IllegalArgumentException unless the limit is between 1 and {@link #MAX_LIMIT}
     */
    public static int checkLimit(final int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "The limit must be between 1 and %d".formatted(MAX_LIMIT));
        }
        return limit;
    }

    /** The same page with every item mapped. */
    public <R> CursorPage<R> map(final Function<? super T, ? extends R> mapper) {
        return new CursorPage<>(items.stream().<R>map(mapper).toList(), next);
    }
}
