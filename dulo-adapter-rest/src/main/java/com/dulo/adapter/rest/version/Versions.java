package com.dulo.adapter.rest.version;

import com.dulo.adapter.rest.error.PreconditionRequiredException;

import org.jspecify.annotations.Nullable;

/**
 * Every changeable resource has a version, sent as its {@code ETag}. Changes must send it back in
 * {@code If-Match}, so a change based on an outdated copy fails instead of overwriting newer data.
 */
public final class Versions {

    public static final String IF_MATCH_DESCRIPTION =
            "The ETag of the resource as last read. A change to an outdated copy fails with 412.";

    private Versions() {}

    /**
     * The version in an {@code If-Match} header: {@code "3"}, also unquoted or weak ({@code
     * W/"3"}).
     *
     * @throws PreconditionRequiredException if the header is missing
     * @throws IllegalArgumentException if it holds no version
     */
    public static long fromIfMatch(final @Nullable String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException();
        }
        final String tag = ifMatch.strip().replaceFirst("^W/", "").replaceAll("^\"|\"$", "");
        try {
            final long version = Long.parseLong(tag);
            if (version >= 0) {
                return version;
            }
        } catch (final NumberFormatException _) {
            // Reported below.
        }
        throw new IllegalArgumentException(
                "If-Match must be the resource's ETag, e.g. \"3\", not %s".formatted(ifMatch));
    }

    /** The ETag value for a version, without quotes; Spring adds them. */
    public static String etag(final long version) {
        return Long.toString(version);
    }
}
