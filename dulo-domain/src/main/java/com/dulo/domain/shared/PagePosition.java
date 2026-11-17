package com.dulo.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a page sorted by a text and then by id ends: the next page starts after it.
 *
 * @param sortKey the sort text of the last item
 * @param id the id of the last item, which breaks ties
 */
public record PagePosition(String sortKey, UUID id) {

    public PagePosition {
        Objects.requireNonNull(sortKey, "sortKey");
        Objects.requireNonNull(id, "id");
    }
}
