package com.dulo.domain.audit;

import java.util.Locale;

/** What happened to a record. */
public enum ChangeKind {
    /** The record was created; the event holds every field. */
    CREATED,
    /** The record was changed; the event holds the fields that differ. */
    CHANGED,
    /** The record was deleted; the event holds every field as it was before. */
    DELETED,
    /**
     * The record was brought back to an earlier state; the event holds the fields that differ from
     * the state before, so it can be replayed like {@link #CHANGED}.
     */
    RESTORED,
    ;

    /** Stable external name, e.g. {@code created}. */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
