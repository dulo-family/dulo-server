package com.dulo.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.security.SecureRandom;
import java.util.Map;

class ConfigurationReportLoggerTest {

    private static DuloProperties bind(final Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("dulo", DuloProperties.class);
    }

    @Test
    void warnsWhenNobodyCanSignIn() {
        assertThat(ConfigurationReportLogger.signInWarnings(bind(Map.of())))
                .hasSize(2)
                .anySatisfy(warning -> assertThat(warning).contains("dulo.identity.providers"))
                .anySatisfy(warning -> assertThat(warning).contains("DULO_CONFIG_FILE"));
    }

    @Test
    void staysQuietWhenSignInIsConfigured() {
        final DuloProperties properties =
                bind(
                        Map.of(
                                "dulo.identity.providers.mock.type", "oidc",
                                "dulo.identity.providers.mock.issuer-uri", "http://idp",
                                "dulo.identity.providers.mock.client-id", "dulo"));

        assertThat(ConfigurationReportLogger.signInWarnings(properties)).isEmpty();
    }

    @Test
    void logsOnlyAGeneratedSetupToken() {
        final SetupToken generated = new SetupToken(null, new SecureRandom());

        assertThat(ConfigurationReportLogger.setupMessage(generated)).endsWith(generated.token());
        assertThat(
                        ConfigurationReportLogger.setupMessage(
                                new SetupToken("configured-secret", new SecureRandom())))
                .contains("dulo.setup.token")
                .doesNotContain("configured-secret");
    }
}
