package com.dulo.domain.shared;

import org.jspecify.annotations.Nullable;

/** Checks for the texts of records that people enter. */
public final class Texts {

    private Texts() {}

    /**
     * The stripped text, or null for a blank or missing one.
     *
     * @param what the field's name, for the error message
     * @throws IllegalArgumentException if the text is longer than the maximum
     */
    public static @Nullable String optional(
            final @Nullable String text, final int maxLength, final String what) {
        if (text == null || text.isBlank()) {
            return null;
        }
        final String stripped = text.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(
                    "%s must be at most %d characters".formatted(what, maxLength));
        }
        return stripped;
    }

    /**
     * The stripped text.
     *
     * @param what the field's name, for the error message
     * @throws IllegalArgumentException if the text is missing, blank or longer than the maximum
     */
    public static String required(
            final @Nullable String text, final int maxLength, final String what) {
        final String stripped = optional(text, maxLength, what);
        if (stripped == null) {
            throw new IllegalArgumentException("%s is required".formatted(what));
        }
        return stripped;
    }
}
