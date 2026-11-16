package com.dulo.core.setup.port.in;

import com.dulo.domain.access.Role;
import com.dulo.domain.feature.FeatureState;
import com.dulo.domain.identity.IdentityProvider;
import com.dulo.domain.identity.RegistrationMode;
import com.dulo.domain.identity.RoleMapping;

import java.util.List;
import java.util.Objects;

/**
 * What the setup proposes, for the setup page to start from.
 *
 * @param roles the built-in roles with their default permissions
 * @param features every feature that can be switched, enabled unless the configuration forces it
 *     off; forced features are locked
 * @param identityProviders the configured providers and how their claims map to roles. These come
 *     from the configuration and can't be changed in the setup.
 * @param registrationMode whether new users are active or wait for approval
 */
public record SetupDefaults(
        List<Role> roles,
        List<FeatureState> features,
        List<ProviderRoles> identityProviders,
        RegistrationMode registrationMode) {

    public SetupDefaults {
        roles = List.copyOf(roles);
        features = List.copyOf(features);
        identityProviders = List.copyOf(identityProviders);
        Objects.requireNonNull(registrationMode, "registrationMode");
    }

    /** A provider and the role mapping its configuration defines. */
    public record ProviderRoles(IdentityProvider provider, RoleMapping roleMapping) {

        public ProviderRoles {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(roleMapping, "roleMapping");
        }
    }
}
