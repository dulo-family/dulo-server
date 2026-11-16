package com.dulo.adapter.identity.authserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.adapter.identity.settings.TestSettings;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

/** The pieces that let the public web client use refresh tokens. */
class PublicClientRefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final RegisteredClientRepository clients =
            new AuthorizationServerConfiguration()
                    .registeredClientRepository(TestSettings.settings());
    private final RegisteredClient web =
            Objects.requireNonNull(this.clients.findByClientId("dulo-web"));
    private final PublicClientRefreshTokenAuthenticationConverter converter =
            new PublicClientRefreshTokenAuthenticationConverter("/oauth2/token");
    private final PublicClientRefreshTokenAuthenticationProvider provider =
            new PublicClientRefreshTokenAuthenticationProvider(this.clients);

    private static MockHttpServletRequest refreshRequest() {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setParameter("grant_type", "refresh_token");
        request.setParameter("refresh_token", "token");
        request.setParameter("client_id", "dulo-web");
        return request;
    }

    @Test
    void convertsAPublicClientsRefreshRequest() {
        final Authentication token = this.converter.convert(refreshRequest());

        assertThat(token)
                .isInstanceOfSatisfying(
                        OAuth2ClientAuthenticationToken.class,
                        t -> {
                            assertThat(t.getPrincipal()).isEqualTo("dulo-web");
                            assertThat(t.getClientAuthenticationMethod())
                                    .isEqualTo(ClientAuthenticationMethod.NONE);
                        });
    }

    @Test
    void leavesOtherRequestsToSpring() {
        final MockHttpServletRequest get = refreshRequest();
        get.setMethod("GET");
        final MockHttpServletRequest code = refreshRequest();
        code.setParameter("grant_type", "authorization_code");
        final MockHttpServletRequest basic = refreshRequest();
        basic.addHeader("Authorization", "Basic abc");
        final MockHttpServletRequest secret = refreshRequest();
        secret.setParameter("client_secret", "s");
        final MockHttpServletRequest anonymous = refreshRequest();
        anonymous.removeParameter("client_id");
        final MockHttpServletRequest introspect = refreshRequest();
        introspect.setRequestURI("/oauth2/introspect");
        final MockHttpServletRequest revoke = refreshRequest();
        revoke.setRequestURI("/oauth2/revoke");

        for (final MockHttpServletRequest request :
                new MockHttpServletRequest[] {
                    get, code, basic, secret, anonymous, introspect, revoke
                }) {
            assertThat(this.converter.convert(request)).isNull();
        }
    }

    @Test
    void rejectsRepeatedClientIds() {
        final MockHttpServletRequest request = refreshRequest();
        request.addParameter("client_id", "other");

        assertThatThrownBy(() -> this.converter.convert(request))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void authenticatesTheRegisteredPublicClient() {
        final Authentication result =
                this.provider.authenticate(this.converter.convert(refreshRequest()));

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(((OAuth2ClientAuthenticationToken) result).getRegisteredClient())
                .isEqualTo(this.web);
        assertThat(this.provider.supports(OAuth2ClientAuthenticationToken.class)).isTrue();
    }

    @Test
    void rejectsUnknownClients() {
        final MockHttpServletRequest request = refreshRequest();
        request.setParameter("client_id", "ghost");

        assertThatThrownBy(() -> this.provider.authenticate(this.converter.convert(request)))
                .isInstanceOfSatisfying(
                        OAuth2AuthenticationException.class,
                        e -> assertThat(e.getError().getErrorCode()).isEqualTo("invalid_client"));
    }

    @Test
    void ignoresOtherClientAuthentications() {
        final OAuth2ClientAuthenticationToken codeGrant =
                new OAuth2ClientAuthenticationToken(
                        "dulo-web",
                        ClientAuthenticationMethod.NONE,
                        null,
                        Map.of("grant_type", "authorization_code"));
        final OAuth2ClientAuthenticationToken basic =
                new OAuth2ClientAuthenticationToken(
                        "dulo-web",
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                        "secret",
                        Map.of("grant_type", "refresh_token"));

        assertThat(this.provider.authenticate(codeGrant)).isNull();
        assertThat(this.provider.authenticate(basic)).isNull();
    }

    @Test
    void generatesRefreshTokensForPublicClients() {
        final PublicClientRefreshTokenGenerator generator =
                new PublicClientRefreshTokenGenerator(Clock.fixed(NOW, ZoneOffset.UTC));
        final DefaultOAuth2TokenContext.Builder context =
                DefaultOAuth2TokenContext.builder()
                        .registeredClient(this.web)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);

        final OAuth2RefreshToken token =
                generator.generate(context.tokenType(OAuth2TokenType.REFRESH_TOKEN).build());

        assertThat(token).isNotNull();
        assertThat(token.getTokenValue()).hasSizeGreaterThan(100);
        assertThat(token.getIssuedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt())
                .isEqualTo(NOW.plus(AuthorizationServerConfiguration.REFRESH_TOKEN_TIME_TO_LIVE));
        assertThat(generator.generate(context.tokenType(OAuth2TokenType.ACCESS_TOKEN).build()))
                .isNull();
    }

    @Test
    void theWebClientIsPublicWithPkceAndRotation() {
        assertThat(this.web.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(this.web.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(this.web.getTokenSettings().isReuseRefreshTokens()).isFalse();
        assertThat(this.web.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(AuthorizationServerConfiguration.ACCESS_TOKEN_TIME_TO_LIVE);
        assertThat(this.clients.findById("dulo-web")).isEqualTo(this.web);
        assertThat(this.clients.findById("ghost")).isNull();
        assertThatThrownBy(() -> this.clients.save(this.web))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
