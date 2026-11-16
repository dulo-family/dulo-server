package com.dulo.core.setup.port.in;

/** The initial setup is done and can't be repeated. */
public class SetupCompletedException extends RuntimeException {

    public SetupCompletedException() {
        super("The initial setup is already done");
    }
}
