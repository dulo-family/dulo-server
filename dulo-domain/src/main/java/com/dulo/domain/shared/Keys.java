package com.dulo.domain.shared;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The stable external names of enum constants, such as {@code also-known-as}. */
public final class Keys {

    private Keys() {}

    /** The key of a constant: its name in lower case, with dashes instead of underscores. */
    public static String key(final Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The constant with the given key, ignoring case. */
    public static <E extends Enum<E>> Optional<E> fromKey(final Class<E> type, final String key) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> key(constant).equalsIgnoreCase(key))
                .findFirst();
    }
}
