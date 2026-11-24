package com.dulo.adapter.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Which provider sign-ins a logout token ends. */
class BackChannelLogoutTest {

    private static final String ISSUER = "https://id.example.com";

    private static OidcIdToken idToken(final String subject, final String sessionId) {
        return OidcIdToken.withTokenValue("id-token")
                .issuer(ISSUER)
                .audience(List.of("dulo"))
                .subject(subject)
                .claim("sid", sessionId)
                .issuedAt(Instant.parse("2026-11-24T10:00:00Z"))
                .build();
    }

    private static OidcLogoutToken.Builder logoutToken() {
        return OidcLogoutToken.withTokenValue("logout-token")
                .issuer(ISSUER)
                .audience(List.of("dulo"))
                .issuedAt(Instant.parse("2026-11-24T11:00:00Z"))
                .jti("logout-1")
                .events(Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
    }

    @Test
    void sessionIdEndsThatProviderSessionOnly() {
        final OidcLogoutToken token = logoutToken().subject("ada").sessionId("s1").build();

        assertThat(BackChannelLogout.endedBy(token, idToken("ada", "s1"))).isTrue();
        assertThat(BackChannelLogout.endedBy(token, idToken("ada", "s2"))).isFalse();
    }

    @Test
    void subjectAloneEndsEverySessionOfTheUser() {
        final OidcLogoutToken token = logoutToken().subject("ada").build();

        assertThat(BackChannelLogout.endedBy(token, idToken("ada", "s1"))).isTrue();
        assertThat(BackChannelLogout.endedBy(token, idToken("ada", "s2"))).isTrue();
        assertThat(BackChannelLogout.endedBy(token, idToken("bob", "s1"))).isFalse();
    }

    @Test
    void otherIssuersAndClientsAreLeftAlone() {
        final OidcLogoutToken otherIssuer =
                logoutToken().issuer("https://other.example.com").subject("ada").build();
        final OidcLogoutToken otherClient =
                logoutToken().audience(List.of("other")).subject("ada").build();

        assertThat(BackChannelLogout.endedBy(otherIssuer, idToken("ada", "s1"))).isFalse();
        assertThat(BackChannelLogout.endedBy(otherClient, idToken("ada", "s1"))).isFalse();
    }
}
