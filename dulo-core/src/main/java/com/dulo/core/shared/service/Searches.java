package com.dulo.core.shared.service;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Search texts of list queries. */
public final class Searches {

    public static final int MAX_LENGTH = 100;

    private Searches() {}

    /**
     * The search text as the repositories expect it: stripped and in lower case, or null for no
     * search.
     *
     * @throws IllegalArgumentException if the text is longer than {@link #MAX_LENGTH}
     */
    public static @Nullable String normalize(final @Nullable String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        final String stripped = search.strip();
        if (stripped.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "A search must be at most %d characters".formatted(MAX_LENGTH));
        }
        return stripped.toLowerCase(Locale.ROOT);
    }
}
