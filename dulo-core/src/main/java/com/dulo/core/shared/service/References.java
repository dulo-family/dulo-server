package com.dulo.core.shared.service;

import java.util.Collection;
import java.util.function.Predicate;

/** Checks that the records a record refers to exist. */
public final class References {

    private References() {}

    /**
     * @param what the kind of record, for the error message, e.g. "person"
     * @throws IllegalArgumentException for the first id that doesn't exist
     */
    public static <T> void requireExisting(
            final String what, final Collection<T> ids, final Predicate<T> exists) {
        ids.stream()
                .filter(id -> !exists.test(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new IllegalArgumentException(
                            "The %s '%s' doesn't exist".formatted(what, id));
                });
    }
}
