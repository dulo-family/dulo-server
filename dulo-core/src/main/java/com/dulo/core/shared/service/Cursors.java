package com.dulo.core.shared.service;

import org.jspecify.annotations.Nullable;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Encodes the position after the last item of a page as an opaque cursor. Clients pass it back
 * unchanged, so its format may change between releases; an old cursor then fails as invalid.
 */
public final class Cursors {

    private static final String SEPARATOR = ",";

    private Cursors() {}

    /** A cursor holding the values, in order. */
    public static String encode(final String... values) {
        final String joined =
                Arrays.stream(values)
                        .map(value -> URLEncoder.encode(value, StandardCharsets.UTF_8))
                        .collect(Collectors.joining(SEPARATOR));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The values of a cursor from {@link #encode}.
     *
     * @param count how many values the cursor must hold
     * @return the values, or null when there is no cursor
     * @throws IllegalArgumentException if the cursor is not one of ours
     */
    public static @Nullable List<String> decode(final @Nullable String cursor, final int count) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            final String joined =
                    new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            final List<String> values =
                    Arrays.stream(joined.split(SEPARATOR, -1))
                            .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
                            .toList();
            if (values.size() != count) {
                throw invalid();
            }
            return values;
        } catch (final IllegalArgumentException _) {
            throw invalid();
        }
    }

    /**
     * The single number a cursor from {@link #encode} holds.
     *
     * @return the number, or null when there is no cursor
     * @throws IllegalArgumentException if the cursor is not one of ours
     */
    public static @Nullable Long decodeLong(final @Nullable String cursor) {
        final List<String> values = decode(cursor, 1);
        if (values == null) {
            return null;
        }
        try {
            return Long.parseLong(values.getFirst());
        } catch (final NumberFormatException _) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid cursor; start again from the first page");
    }
}
