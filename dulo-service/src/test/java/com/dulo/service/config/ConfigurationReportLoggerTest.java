package com.dulo.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

class ConfigurationReportLoggerTest {

    private static DuloProperties bind(final Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("dulo", DuloProperties.class);
    }

    @Test
    void warnsWhenNobodyCanSignIn() {
        assertThat(ConfigurationReportLogger.signInWarnings(bind(Map.of())))
                .hasSize(3)
                .anySatisfy(warning -> assertThat(warning).contains("dulo.identity.providers"))
                .anySatisfy(
                        warning -> assertThat(warning).contains("dulo.clients.web.redirect-uris"))
                .anySatisfy(warning -> assertThat(warning).contains("DULO_CONFIG_FILE"));
    }

    @Test
    void staysQuietWhenSignInIsConfigured() {
        final DuloProperties properties =
                bind(
                        Map.of(
                                "dulo.identity.providers.mock.type", "oidc",
                                "dulo.identity.providers.mock.issuer-uri", "http://idp",
                                "dulo.identity.providers.mock.client-id", "dulo",
                                "dulo.clients.web.redirect-uris[0]", "http://web/callback"));

        assertThat(ConfigurationReportLogger.signInWarnings(properties)).isEmpty();
    }
}
