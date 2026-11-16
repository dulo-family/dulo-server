package com.dulo.domain.access;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What a user may do within a {@link com.dulo.domain.feature.Feature}. */
public enum Access {
    VIEW,
    CREATE,
    EDIT,
    DELETE,
    ;

    /** The access with the given {@link #key()}, ignoring case. */
    public static Optional<Access> fromKey(final String key) {
        return Arrays.stream(values()).filter(a -> a.key().equalsIgnoreCase(key)).findFirst();
    }

    /** Stable external name, e.g. {@code view}. */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
