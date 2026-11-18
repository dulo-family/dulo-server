package com.dulo.core.audit.recording;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Typed access to the fields of a record, as {@link RestorableFields} turns them back. */
public final class FieldReader {

    private FieldReader() {}

    /**
     * @throws IllegalArgumentException if the field is missing or not a text
     */
    public static String text(final Map<?, ?> fields, final String field) {
        final String value = optionalText(fields, field);
        if (value == null) {
            throw new IllegalArgumentException("The field '%s' is missing".formatted(field));
        }
        return value;
    }

    /**
     * @throws IllegalArgumentException if the field is not a text
     */
    public static @Nullable String optionalText(final Map<?, ?> fields, final String field) {
        return typed(fields, field, String.class);
    }

    /**
     * @throws IllegalArgumentException if the field is not a number
     */
    public static @Nullable Integer optionalInt(final Map<?, ?> fields, final String field) {
        final Number value = typed(fields, field, Number.class);
        return value == null ? null : value.intValue();
    }

    /**
     * @throws IllegalArgumentException if the field is missing or not a number
     */
    public static double number(final Map<?, ?> fields, final String field) {
        final Number value = typed(fields, field, Number.class);
        if (value == null) {
            throw new IllegalArgumentException("The field '%s' is missing".formatted(field));
        }
        return value.doubleValue();
    }

    /**
     * @throws IllegalArgumentException if the field is not a map
     */
    public static @Nullable Map<?, ?> optionalMap(final Map<?, ?> fields, final String field) {
        return typed(fields, field, Map.class);
    }

    /**
     * The list's elements, each mapped; empty when the field is missing.
     *
     * @throws IllegalArgumentException if the field is not a list
     */
    public static <T> List<T> list(
            final Map<?, ?> fields, final String field, final Function<Object, T> element) {
        final List<?> value = typed(fields, field, List.class);
        return value == null ? List.of() : value.stream().map(element).toList();
    }

    /** The element as a map, for {@link #list}. */
    public static Map<?, ?> asMap(final Object element) {
        if (element instanceof final Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("Expected a map, found " + element);
    }

    /** The constant with the given key, through the enum's {@code fromKey}. */
    public static <E> E constant(
            final String key, final String what, final Function<String, Optional<E>> fromKey) {
        return fromKey.apply(key)
                .orElseThrow(
                        () -> new IllegalArgumentException("Unknown %s '%s'".formatted(what, key)));
    }

    private static <T> @Nullable T typed(
            final Map<?, ?> fields, final String field, final Class<T> type) {
        final Object value = fields.get(field);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "The field '%s' is not a %s".formatted(field, type.getSimpleName()));
        }
        return type.cast(value);
    }
}
