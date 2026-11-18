package com.dulo.core.shared.port.in;

/** A change to an existing record needs the version the caller saw, and none was given. */
public class VersionRequiredException extends RuntimeException {

    public VersionRequiredException(final String what, final Object id) {
        super("The %s '%s' exists, so changing it needs the version you saw".formatted(what, id));
    }
}
