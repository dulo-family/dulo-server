package com.dulo.adapter.rest.error;

/** A change came without If-Match. */
public class PreconditionRequiredException extends RuntimeException {

    public PreconditionRequiredException() {
        super("Send the resource's ETag in If-Match, so a change can't overwrite a newer one");
    }
}
