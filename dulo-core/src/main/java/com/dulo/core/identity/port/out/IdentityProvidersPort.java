package com.dulo.core.identity.port.out;

import com.dulo.domain.identity.IdentityProvider;
import com.dulo.domain.identity.RegistrationMode;
import com.dulo.domain.identity.RoleMapping;

import java.util.List;
import java.util.Optional;

/** The identity providers in the deployment configuration. */
public interface IdentityProvidersPort {

    /** All configured providers, sorted by id. */
    List<IdentityProvider> providers();

    /** The role mapping of a provider, or empty when the provider is not configured. */
    Optional<RoleMapping> roleMapping(String providerId);

    RegistrationMode registrationMode();
}
