package com.dulo.adapter.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import com.dulo.adapter.identity.settings.TestSettings;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

class ProviderClientRegistrationsTest {

    private final ProviderClientRegistrations registrations =
            new ProviderClientRegistrations(
                    TestSettings.settings(
                            TestSettings.provider("google", "secret"),
                            TestSettings.provider("public", null)));

    @Test
    void registersGoogleWithoutDiscovery() {
        final ClientRegistration google = this.registrations.findByRegistrationId("google");

        assertThat(google).isNotNull();
        assertThat(google.getClientId()).isEqualTo("client-google");
        assertThat(google.getClientSecret()).isEqualTo("secret");
        assertThat(google.getClientName()).isEqualTo("Provider google");
        assertThat(google.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(google.getRedirectUri())
                .isEqualTo("https://tree.example.com/login/oauth2/code/{registrationId}");
        assertThat(this.registrations.findByRegistrationId("google")).isSameAs(google);
    }

    @Test
    void providersWithoutSecretArePublicClients() {
        final ClientRegistration registration = this.registrations.findByRegistrationId("public");

        assertThat(registration).isNotNull();
        assertThat(registration.getClientAuthenticationMethod())
                .isEqualTo(ClientAuthenticationMethod.NONE);
    }

    @Test
    void unknownProvidersAreNotFound() {
        assertThat(this.registrations.findByRegistrationId("ghost")).isNull();
    }
}
