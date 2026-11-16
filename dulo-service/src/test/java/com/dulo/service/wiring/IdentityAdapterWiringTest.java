package com.dulo.service.wiring;

import static org.assertj.core.api.Assertions.assertThat;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.Provider;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.ProviderType;
import com.dulo.service.config.DuloProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.net.URI;
import java.util.Map;

class IdentityAdapterWiringTest {

    private static IdentityAdapterSettings settings(final Map<String, String> values) {
        return IdentityAdapterWiring.settings(
                new Binder(new MapConfigurationPropertySource(values))
                        .bindOrCreate("dulo", DuloProperties.class));
    }

    @Test
    void mapsProvidersSortedById() {
        final IdentityAdapterSettings settings =
                settings(
                        Map.of(
                                "dulo.identity.providers.pocketid.type", "oidc",
                                "dulo.identity.providers.pocketid.issuer-uri",
                                        "https://id.example.com",
                                "dulo.identity.providers.pocketid.client-id", "dulo",
                                "dulo.identity.providers.pocketid.client-secret", "s3cret",
                                "dulo.identity.providers.pocketid.claims.roles", "roles",
                                "dulo.identity.providers.google.type", "google",
                                "dulo.identity.providers.google.client-id", "abc",
                                "dulo.identity.providers.entra.type", "microsoft",
                                "dulo.identity.providers.entra.issuer-uri",
                                        "https://login.microsoftonline.com/t/v2.0",
                                "dulo.identity.providers.entra.client-id", "def"));

        assertThat(settings.providers())
                .extracting(Provider::id)
                .containsExactly("entra", "google", "pocketid");
        final Provider pocketId = settings.provider("pocketid").orElseThrow();
        assertThat(pocketId.type()).isEqualTo(ProviderType.OIDC);
        assertThat(pocketId.displayName()).isEqualTo("pocketid");
        assertThat(pocketId.clientSecret()).isEqualTo("s3cret");
        assertThat(pocketId.claims().roles()).isEqualTo("roles");
        assertThat(pocketId.scopes()).containsExactly("openid", "profile", "email");
        assertThat(settings.provider("google").orElseThrow().displayName()).isEqualTo("Google");
        assertThat(settings.provider("entra").orElseThrow().displayName()).isEqualTo("Microsoft");
    }

    @Test
    void signInUrlDefaultsToTheWebClientUnderThePublicUrl() {
        assertThat(
                        settings(Map.of("dulo.public-url", "https://tree.example.com/"))
                                .webClient()
                                .signInUrl())
                .isEqualTo(URI.create("https://tree.example.com/sign-in"));
        assertThat(
                        settings(
                                        Map.of(
                                                "dulo.clients.web.sign-in-url",
                                                "https://web.example.com/login"))
                                .webClient()
                                .signInUrl())
                .isEqualTo(URI.create("https://web.example.com/login"));
    }

    @Test
    void mapsTheWebClient() {
        final IdentityAdapterSettings settings =
                settings(
                        Map.of(
                                "dulo.clients.web.redirect-uris[0]", "https://tree.example.com/cb",
                                "dulo.clients.web.allowed-origins[0]", "https://tree.example.com"));

        assertThat(settings.webClient().clientId()).isEqualTo("dulo-web");
        assertThat(settings.webClient().redirectUris())
                .containsExactly(URI.create("https://tree.example.com/cb"));
        assertThat(settings.webClient().allowedOrigins())
                .containsExactly("https://tree.example.com");
        assertThat(settings.publicUrl()).isEqualTo(URI.create("http://localhost:8080"));
    }
}
