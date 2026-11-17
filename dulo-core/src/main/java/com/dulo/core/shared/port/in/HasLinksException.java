package com.dulo.core.shared.port.in;

import com.dulo.domain.audit.Link;

import java.util.List;

/**
 * A record can't be deleted without confirmation, because other records still refer to it. A
 * confirmed deletion removes those references too.
 */
public class HasLinksException extends RuntimeException {

    private final transient List<Link> links;

    public HasLinksException(final String what, final Object id, final List<Link> links) {
        super(
                ("The %s '%s' is still linked from %d records; confirm the deletion to remove"
                                + " those links too")
                        .formatted(what, id, links.size()));
        this.links = List.copyOf(links);
    }

    /** The references to the record. */
    public List<Link> links() {
        return this.links;
    }
}
