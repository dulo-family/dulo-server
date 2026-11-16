package com.dulo.adapter.identity.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityAdapterSettingsTest {

    @Test
    void buildsUrlsWithoutDoubleSlashes() {
        assertThat(TestSettings.settings().url("/login/oauth2/code/{registrationId}"))
                .isEqualTo("https://tree.example.com/login/oauth2/code/{registrationId}");
    }

    @Test
    void findsProvidersById() {
        final IdentityAdapterSettings settings =
                TestSettings.settings(TestSettings.provider("google", "secret"));

        assertThat(settings.provider("google")).isPresent();
        assertThat(settings.provider("ghost")).isEmpty();
    }
}
