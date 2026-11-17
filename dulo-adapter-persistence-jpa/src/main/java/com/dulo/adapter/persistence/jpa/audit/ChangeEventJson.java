package com.dulo.adapter.persistence.jpa.audit;

import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.audit.Link;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON stored in the {@code changes} and {@code removed_links} columns.
 *
 * <p>Format version 1:
 *
 * <ul>
 *   <li>{@code changes}: {@code [{"field": "name", "before": "Ada", "after": "Ada L."}]}, where
 *       {@code before} and {@code after} are null when the field has no value;
 *   <li>{@code removed_links}: {@code [{"entityType": "event", "entityId": "…", "field":
 *       "place"}]}.
 * </ul>
 *
 * A change to this format needs a new {@link #FORMAT_VERSION}, and reading must upcast the rows of
 * the older versions.
 */
final class ChangeEventJson {

    static final int FORMAT_VERSION = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<Map<String, @Nullable Object>>> ROWS =
            new TypeReference<>() {};

    private ChangeEventJson() {}

    static String changes(final List<FieldChange> changes) {
        return MAPPER.writeValueAsString(
                changes.stream()
                        .map(change -> {
                            final Map<String, @Nullable Object> row = new LinkedHashMap<>();
                            row.put("field", change.field());
                            row.put("before", change.before());
                            row.put("after", change.after());
                            return row;
                        })
                        .toList());
    }

    static List<FieldChange> changes(final int formatVersion, final String json) {
        checkFormat(formatVersion);
        return MAPPER.readValue(json, ROWS).stream()
                .map(row ->
                        new FieldChange(
                                (String) required(row, "field"),
                                row.get("before"),
                                row.get("after")))
                .toList();
    }

    static String links(final List<Link> links) {
        return MAPPER.writeValueAsString(
                links.stream()
                        .map(link -> {
                            final Map<String, @Nullable Object> row = new LinkedHashMap<>();
                            row.put("entityType", link.entityType().key());
                            row.put("entityId", link.entityId());
                            row.put("field", link.field());
                            return row;
                        })
                        .toList());
    }

    static List<Link> links(final int formatVersion, final String json) {
        checkFormat(formatVersion);
        return MAPPER.readValue(json, ROWS).stream()
                .map(row ->
                        new Link(
                                entityType((String) required(row, "entityType")),
                                (String) required(row, "entityId"),
                                (String) required(row, "field")))
                .toList();
    }

    static EntityType entityType(final String key) {
        return EntityType.fromKey(key)
                .orElseThrow(
                        () -> new IllegalStateException("Unknown entity type '%s'".formatted(key)));
    }

    private static void checkFormat(final int formatVersion) {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalStateException(
                    "Unknown audit format version %d; this server reads version %d"
                            .formatted(formatVersion, FORMAT_VERSION));
        }
    }

    private static Object required(final Map<String, @Nullable Object> row, final String key) {
        final Object value = row.get(key);
        if (value == null) {
            throw new IllegalStateException("Audit row without '%s'".formatted(key));
        }
        return value;
    }
}
