package com.dulo.adapter.persistence.jpa.shared;

import com.dulo.domain.shared.PagePosition;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds JPQL conditions for lists sorted by a text column and then by id, read in pages. Ids are
 * compared as text, which is how the database sorts them.
 */
public final class Keyset {

    private static final char ESCAPE = '!';

    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> parameters = new LinkedHashMap<>();

    /** A {@code like} pattern that finds the text anywhere, with its wildcards escaped. */
    public static String containing(final String text) {
        final StringBuilder pattern = new StringBuilder("%");
        for (final char c : text.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                pattern.append(ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }

    /** The escape clause that goes with {@link #containing}. */
    public static String escape() {
        return " escape '" + ESCAPE + "'";
    }

    /** Adds a condition with its parameters. */
    public Keyset where(final String condition, final Map<String, Object> parameters) {
        this.conditions.add(condition);
        this.parameters.putAll(parameters);
        return this;
    }

    /**
     * Only rows after the position.
     *
     * @param sortColumn the JPQL path of the sort column, such as {@code p.sortName}
     * @param idColumn the JPQL path of the id, such as {@code p.id}
     */
    public Keyset after(
            final @Nullable PagePosition position, final String sortColumn, final String idColumn) {
        if (position == null) {
            return this;
        }
        return this.where(
                "(%1$s > :afterSort or (%1$s = :afterSort and %2$s > :afterId))"
                        .formatted(sortColumn, idColumn),
                Map.of("afterSort", position.sortKey(), "afterId", position.id().toString()));
    }

    /** The conditions, joined with {@code and}, as a where clause; empty for none. */
    public String whereClause() {
        return this.conditions.isEmpty() ? "" : " where " + String.join(" and ", this.conditions);
    }

    public Map<String, Object> parameters() {
        return Map.copyOf(this.parameters);
    }
}
