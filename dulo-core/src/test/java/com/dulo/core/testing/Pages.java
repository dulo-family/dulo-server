package com.dulo.core.testing;

import com.dulo.domain.shared.PagePosition;

import java.util.UUID;

/** Keyset comparisons, as the database does them: ids are compared as text. */
final class Pages {

    private Pages() {}

    /** Whether an item sorts after the position: by sort key, then by id. */
    static boolean isAfter(final String sortKey, final UUID id, final PagePosition position) {
        final int bySortKey = sortKey.compareTo(position.sortKey());
        return bySortKey > 0
                || (bySortKey == 0 && id.toString().compareTo(position.id().toString()) > 0);
    }
}
