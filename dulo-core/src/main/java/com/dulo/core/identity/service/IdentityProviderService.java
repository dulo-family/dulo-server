package com.dulo.core.identity.service;

import com.dulo.core.identity.port.in.ListIdentityProvidersUseCase;
import com.dulo.core.identity.port.out.IdentityProvidersPort;
import com.dulo.domain.identity.IdentityProvider;

import java.util.List;

public class IdentityProviderService implements ListIdentityProvidersUseCase {

    private final IdentityProvidersPort providers;

    public IdentityProviderService(final IdentityProvidersPort providers) {
        this.providers = providers;
    }

    @Override
    public List<IdentityProvider> identityProviders() {
        return this.providers.providers();
    }
}
