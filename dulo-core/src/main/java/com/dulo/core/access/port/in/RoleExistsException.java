package com.dulo.core.access.port.in;

import com.dulo.domain.access.RoleKey;

/** A role with the key already exists. */
public class RoleExistsException extends RuntimeException {

    public RoleExistsException(final RoleKey key) {
        super("A role with the key '%s' already exists".formatted(key));
    }
}
