package com.dulo.core.setup.port.in;

public interface GetSetupDefaultsUseCase {

    /**
     * The roles, features and role mappings the setup starts from.
     *
     * @param setupToken the one-time setup token
     * @throws SetupCompletedException if the setup is done
     * @throws InvalidSetupTokenException if the token is wrong
     */
    SetupDefaults defaults(String setupToken);
}
