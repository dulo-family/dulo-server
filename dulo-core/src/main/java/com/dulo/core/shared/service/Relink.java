package com.dulo.core.shared.service;

import com.dulo.core.audit.recording.ChangeSet;
import com.dulo.core.audit.recording.RecordFields;

import java.util.function.UnaryOperator;

/**
 * A link that a restore puts back into another record: the other record before and after, and how
 * to store it.
 */
public record Relink<T>(RecordFields<T> fields, T before, T after, UnaryOperator<T> save) {

    /** Stores the other record and records its change. */
    public void apply(final ChangeSet changes) {
        changes.changed(this.fields, this.before, this.save.apply(this.after));
    }
}
