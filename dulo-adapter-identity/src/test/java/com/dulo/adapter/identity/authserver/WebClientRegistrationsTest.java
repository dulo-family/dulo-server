package com.dulo.adapter.identity.authserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.adapter.identity.settings.TestSettings;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.List;

class WebClientRegistrationsTest {

    @Test
    void theWebClientAlwaysExists() {
        final RegisteredClientRepository clients =
                new AuthorizationServerConfiguration()
                        .registeredClientRepository(TestSettings.settings());

        final RegisteredClient client = clients.findByClientId("dulo-web");
        assertThat(client).isNotNull();
        assertThat(client.getRedirectUris())
                .containsExactly("https://tree.example.com/auth/callback");
        assertThat(clients.findById("dulo-web")).isSameAs(client);
        assertThat(clients.findByClientId("other")).isNull();
        assertThat(clients.findById("other")).isNull();
    }

    @Test
    void theWebClientNeedsARedirectUri() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new IdentityAdapterSettings.WebClient(
                                "dulo-web", List.of(), List.of(), TestSettings.SIGN_IN_URL))
                .withMessageContaining("redirect URI");
    }
}
