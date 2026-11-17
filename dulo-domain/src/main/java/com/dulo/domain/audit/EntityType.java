package com.dulo.domain.audit;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The kinds of records the audit history covers. */
public enum EntityType {
    EVENT(false),
    FAMILY(false),
    PERSON(false),
    PLACE(false),
    /** A role: an administrative record. */
    ROLE(true),
    /** The tree settings: an administrative record. */
    TREE(true),
    /** A user account: an administrative record. */
    USER(true),
    ;

    private final boolean administrative;

    EntityType(final boolean administrative) {
        this.administrative = administrative;
    }

    /** The type with the given {@link #key()}, ignoring case. */
    public static Optional<EntityType> fromKey(final String key) {
        return Arrays.stream(values()).filter(t -> t.key().equalsIgnoreCase(key)).findFirst();
    }

    /**
     * Whether the records belong to the administration of the instance rather than to the family
     * tree; their history is visible only to administrators.
     */
    public boolean administrative() {
        return administrative;
    }

    /** Stable external name, e.g. {@code person}. */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
