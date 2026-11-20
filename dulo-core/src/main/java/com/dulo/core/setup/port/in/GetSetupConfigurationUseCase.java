package com.dulo.core.setup.port.in;

public interface GetSetupConfigurationUseCase {

    /**
     * The server's merged configuration, with secrets masked and every source of each value, so the
     * person doing the setup can check it.
     *
     * @param setupToken the one-time setup token
     * @throws SetupCompletedException if the setup is done
     * @throws InvalidSetupTokenException if the token is wrong
     */
    String configuration(String setupToken);
}
