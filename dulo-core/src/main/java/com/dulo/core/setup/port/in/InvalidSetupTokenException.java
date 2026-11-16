package com.dulo.core.setup.port.in;

/** The setup token is missing or wrong. */
public class InvalidSetupTokenException extends RuntimeException {

    public InvalidSetupTokenException() {
        super(
                "The setup token is missing or wrong. The server logs it on start, unless it is"
                        + " set in the configuration (dulo.setup.token).");
    }
}
