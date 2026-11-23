package com.dulo.service.wiring;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.Claims;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.Provider;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.ProviderType;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.WebClient;
import com.dulo.service.config.DuloProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Maps the Dulo configuration to the settings of the identity adapter. */
@Configuration(proxyBeanMethods = false)
public class IdentityAdapterWiring {

    /** Path of the web client's sign-in callback, used when no redirect URI is configured. */
    static final String DEFAULT_CALLBACK_PATH = "/auth/callback";

    /** Path of the web client's sign-in page, used when no sign-in URL is configured. */
    static final String DEFAULT_SIGN_IN_PATH = "/sign-in";

    static IdentityAdapterSettings settings(final DuloProperties properties) {
        final URI publicUrl = properties.publicUrl();
        final List<Provider> providers =
                properties.identity().providers().entrySet().stream()
                        .map(e -> provider(e.getKey(), e.getValue()))
                        .sorted(Comparator.comparing(Provider::id))
                        .toList();
        final DuloProperties.WebClient web = properties.clients().web();
        // Without configuration, the web client is served under the public URL, as in the image.
        final String base = publicUrl.toString().replaceAll("/+$", "");
        final List<URI> redirectUris =
                web.redirectUris().isEmpty()
                        ? List.of(URI.create(base + DEFAULT_CALLBACK_PATH))
                        : web.redirectUris();
        final URI signInUrl =
                Optional.ofNullable(web.signInUrl())
                        .orElseGet(() -> URI.create(base + DEFAULT_SIGN_IN_PATH));
        return new IdentityAdapterSettings(
                publicUrl,
                providers,
                new WebClient(web.clientId(), redirectUris, web.allowedOrigins(), signInUrl));
    }

    private static Provider provider(final String id, final DuloProperties.Provider provider) {
        return new Provider(
                id,
                switch (provider.type()) {
                    case OIDC -> ProviderType.OIDC;
                    case GOOGLE -> ProviderType.GOOGLE;
                    case MICROSOFT -> ProviderType.MICROSOFT;
                },
                provider.displayNameOr(id),
                provider.issuerUri(),
                provider.clientId(),
                provider.clientSecret(),
                provider.scopes(),
                new Claims(
                        provider.claims().roles(),
                        provider.claims().email(),
                        provider.claims().name()));
    }

    @Bean
    IdentityAdapterSettings identityAdapterSettings(final DuloProperties properties) {
        return settings(properties);
    }
}
