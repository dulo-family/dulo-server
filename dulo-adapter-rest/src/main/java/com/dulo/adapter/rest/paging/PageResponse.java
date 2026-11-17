package com.dulo.adapter.rest.paging;

import com.dulo.domain.shared.CursorPage;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list. Read the next page by passing {@code next} as the {@code after} parameter.
 */
public record PageResponse<T>(
        List<T> items,
        @Schema(
                        description =
                                "Opaque cursor of the next page: pass it as `after`. Null on the"
                                        + " last page.")
                @Nullable String next) {

    /** The OpenAPI description of the {@code limit} parameter. */
    public static final String LIMIT_DESCRIPTION = "Page size, 1 to " + CursorPage.MAX_LIMIT + ".";

    /** The OpenAPI description of the {@code after} parameter. */
    public static final String AFTER_DESCRIPTION =
            "The `next` cursor of the previous page; leave it out for the first page.";

    /** The default of the {@code limit} parameter, as Spring needs it: a constant string. */
    public static final String DEFAULT_LIMIT = "" + CursorPage.DEFAULT_LIMIT;

    public static <D, T> PageResponse<T> from(
            final CursorPage<D> page, final Function<? super D, ? extends T> mapper) {
        return new PageResponse<>(page.items().stream().<T>map(mapper).toList(), page.next());
    }
}
