package com.dulo.adapter.rest.error;

/** The API is closed until the initial setup is done. */
public class SetupRequiredException extends RuntimeException {

    public SetupRequiredException() {
        super("The initial setup isn't done yet. Complete it at /api/v1/setup first.");
    }
}
