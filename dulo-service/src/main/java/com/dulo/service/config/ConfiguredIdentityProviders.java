package com.dulo.service.config;

import com.dulo.core.identity.port.out.IdentityProvidersPort;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.identity.IdentityProvider;
import com.dulo.domain.identity.IdentityProviderType;
import com.dulo.domain.identity.RegistrationMode;
import com.dulo.domain.identity.RoleMapping;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The identity providers of {@code dulo.identity.providers}, as the core sees them. Role keys are
 * checked when the server starts.
 */
class ConfiguredIdentityProviders implements IdentityProvidersPort {

    private final List<IdentityProvider> providers;
    private final Map<String, RoleMapping> roleMappings;
    private final RegistrationMode registrationMode;

    ConfiguredIdentityProviders(final DuloProperties properties) {
        final Map<String, RoleMapping> mappings = new HashMap<>();
        properties
                .identity()
                .providers()
                .forEach((id, provider) -> mappings.put(id, roleMapping(id, provider)));
        this.roleMappings = Map.copyOf(mappings);
        this.providers =
                properties.identity().providers().entrySet().stream()
                        .map(e ->
                                new IdentityProvider(
                                        e.getKey(),
                                        e.getValue().displayNameOr(e.getKey()),
                                        type(e.getValue().type())))
                        .sorted(Comparator.comparing(IdentityProvider::id))
                        .toList();
        this.registrationMode =
                switch (properties.registration().mode()) {
                    case AUTO -> RegistrationMode.AUTO;
                    case APPROVAL -> RegistrationMode.APPROVAL;
                };
    }

    private static IdentityProviderType type(final DuloProperties.ProviderType type) {
        return switch (type) {
            case OIDC -> IdentityProviderType.OIDC;
            case GOOGLE -> IdentityProviderType.GOOGLE;
            case MICROSOFT -> IdentityProviderType.MICROSOFT;
        };
    }

    private static RoleMapping roleMapping(
            final String providerId, final DuloProperties.Provider provider) {
        try {
            final Map<String, Set<RoleKey>> byClaimValue = new HashMap<>();
            provider.roleMappings()
                    .forEach((claimValue, roles) ->
                            byClaimValue.put(
                                    claimValue,
                                    roles.stream().map(RoleKey::new).collect(Collectors.toSet())));
            final String defaultRole = provider.defaultRole();
            return new RoleMapping(
                    byClaimValue, defaultRole == null ? null : new RoleKey(defaultRole));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid role in dulo.identity.providers.%s: %s"
                            .formatted(providerId, e.getMessage()),
                    e);
        }
    }

    @Override
    public List<IdentityProvider> providers() {
        return this.providers;
    }

    @Override
    public Optional<RoleMapping> roleMapping(final String providerId) {
        return Optional.ofNullable(this.roleMappings.get(providerId));
    }

    @Override
    public RegistrationMode registrationMode() {
        return this.registrationMode;
    }
}
