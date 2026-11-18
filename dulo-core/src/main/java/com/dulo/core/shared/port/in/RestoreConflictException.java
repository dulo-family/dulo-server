package com.dulo.core.shared.port.in;

import com.dulo.domain.audit.Link;

import java.util.List;

/**
 * A record can't be restored completely without confirmation: some references it had can't come
 * back, because the other record is gone or now refers elsewhere. A confirmed restore leaves them
 * out.
 */
public class RestoreConflictException extends RuntimeException {

    private final transient List<Link> links;

    public RestoreConflictException(final String what, final Object id, final List<Link> links) {
        super(
                ("The %s '%s' can't get back %d of its links; confirm the restore to leave them"
                                + " out")
                        .formatted(what, id, links.size()));
        this.links = List.copyOf(links);
    }

    /** The references that can't come back: the other record, and the field involved. */
    public List<Link> links() {
        return this.links;
    }
}
