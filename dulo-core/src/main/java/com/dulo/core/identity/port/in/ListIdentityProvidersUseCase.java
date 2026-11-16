package com.dulo.core.identity.port.in;

import com.dulo.domain.identity.IdentityProvider;

import java.util.List;

/** The identity providers users can sign in with. Public, so the sign-in page can show them. */
public interface ListIdentityProvidersUseCase {

    /** All configured providers, sorted by id. */
    List<IdentityProvider> identityProviders();
}
