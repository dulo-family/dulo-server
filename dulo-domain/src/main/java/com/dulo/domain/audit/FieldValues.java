package com.dulo.domain.audit;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The values a {@link FieldChange} may hold: the ones JSON can represent without loss, so events
 * can be stored and read back unchanged.
 *
 * <ul>
 *   <li>{@link String}, {@link Boolean}, {@link Long} and {@link Double}; {@link Integer} becomes
 *       {@link Long};
 *   <li>a {@link List} of such values;
 *   <li>a {@link Map} with {@link String} keys and such values, in key order.
 * </ul>
 *
 * Lists and maps contain no nulls: a map leaves out a key whose value is null.
 */
public final class FieldValues {

    private FieldValues() {}

    /**
     * An unmodifiable copy of the value.
     *
     * @throws IllegalArgumentException if the value or anything in it is not allowed
     */
    public static @Nullable Object copy(final @Nullable Object value) {
        return value == null ? null : copyNonNull(value);
    }

    private static Object copyNonNull(final Object value) {
        return switch (value) {
            case final String s -> s;
            case final Boolean b -> b;
            case final Long l -> l;
            case final Integer i -> i.longValue();
            case final Double d -> {
                if (d.isNaN() || d.isInfinite()) {
                    throw new IllegalArgumentException("Field values must be finite numbers");
                }
                yield d;
            }
            case final List<?> list -> {
                final List<Object> copy = new ArrayList<>(list.size());
                for (final Object element : list) {
                    if (element == null) {
                        throw new IllegalArgumentException(
                                "Lists in field values contain no nulls");
                    }
                    copy.add(copyNonNull(element));
                }
                yield Collections.unmodifiableList(copy);
            }
            case final Map<?, ?> map -> {
                final Map<String, Object> copy = new LinkedHashMap<>();
                map.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(FieldValues::compareKeys))
                        .forEach(entry -> {
                            if (!(entry.getKey() instanceof final String key)) {
                                throw new IllegalArgumentException(
                                        "Maps in field values have string keys");
                            }
                            if (entry.getValue() == null) {
                                throw new IllegalArgumentException(
                                        "Maps in field values leave out null values");
                            }
                            copy.put(key, copyNonNull(entry.getValue()));
                        });
                yield Collections.unmodifiableMap(copy);
            }
            default ->
                    throw new IllegalArgumentException(
                            "Field values can't hold a " + value.getClass().getSimpleName());
        };
    }

    private static int compareKeys(final Object a, final Object b) {
        return String.valueOf(a).compareTo(String.valueOf(b));
    }
}
