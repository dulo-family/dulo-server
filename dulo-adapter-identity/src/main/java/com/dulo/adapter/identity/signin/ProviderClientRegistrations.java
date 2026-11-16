package com.dulo.adapter.identity.signin;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.Provider;

import org.jspecify.annotations.Nullable;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dulo's registrations at the upstream identity providers. OIDC providers are discovered through
 * their issuer on first use, not on start, so a provider that is down does not stop the server. Any
 * number of providers works, including none.
 */
class ProviderClientRegistrations implements ClientRegistrationRepository {

    private final IdentityAdapterSettings settings;
    private final Map<String, ClientRegistration> registrations = new ConcurrentHashMap<>();

    ProviderClientRegistrations(final IdentityAdapterSettings settings) {
        this.settings = settings;
    }

    @Override
    public @Nullable ClientRegistration findByRegistrationId(final String registrationId) {
        return this.settings
                .provider(registrationId)
                .map(p -> this.registrations.computeIfAbsent(p.id(), _ -> this.register(p)))
                .orElse(null);
    }

    private ClientRegistration register(final Provider provider) {
        final ClientRegistration.Builder builder =
                switch (provider.type()) {
                    case GOOGLE -> CommonOAuth2Provider.GOOGLE.getBuilder(provider.id());
                    case OIDC, MICROSOFT ->
                            ClientRegistrations.fromOidcIssuerLocation(
                                            Objects.requireNonNull(
                                                            provider.issuerUri(), "issuerUri")
                                                    .toString())
                                    .registrationId(provider.id());
                };
        // Dulo needs the ID token, so openid is always requested.
        final Set<String> scopes = new LinkedHashSet<>();
        scopes.add(OidcScopes.OPENID);
        scopes.addAll(provider.scopes());
        builder.clientId(provider.clientId())
                .clientName(provider.displayName())
                .scope(scopes)
                .redirectUri(this.settings.url("/login/oauth2/code/{registrationId}"));
        final String secret = provider.clientSecret();
        if (secret == null) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else {
            builder.clientSecret(secret);
        }
        return builder.build();
    }
}
