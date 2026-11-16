package com.dulo.domain.shared;

/** The requested thing doesn't exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(final String what, final Object id) {
        super("%s '%s' doesn't exist".formatted(what, id));
    }
}
