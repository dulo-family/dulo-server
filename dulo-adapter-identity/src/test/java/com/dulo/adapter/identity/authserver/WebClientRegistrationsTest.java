package com.dulo.adapter.identity.authserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.adapter.identity.settings.TestSettings;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.List;

class WebClientRegistrationsTest {

    @Test
    void noRedirectUriMeansNoClient() {
        final IdentityAdapterSettings settings = TestSettings.settings();
        final IdentityAdapterSettings withoutRedirects =
                new IdentityAdapterSettings(
                        settings.publicUrl(),
                        List.of(),
                        new IdentityAdapterSettings.WebClient(
                                "dulo-web", List.of(), List.of(), TestSettings.SIGN_IN_URL));

        final RegisteredClientRepository clients =
                new AuthorizationServerConfiguration().registeredClientRepository(withoutRedirects);

        assertThat(clients.findByClientId("dulo-web")).isNull();
        assertThat(clients.findById("dulo-web")).isNull();
    }
}
