package com.dulo.adapter.identity.authserver;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.time.Clock;
import java.time.Duration;

/**
 * Dulo's own OAuth2 authorization server. It issues JWT access tokens, whose subject is the Dulo
 * user id, to Dulo's clients. Authorizations, codes and refresh tokens are kept in memory, so after
 * a restart users sign in again.
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfiguration {

    static final Duration ACCESS_TOKEN_TIME_TO_LIVE = Duration.ofMinutes(15);
    static final Duration REFRESH_TOKEN_TIME_TO_LIVE = Duration.ofDays(30);

    @Bean
    AuthorizationServerSettings authorizationServerSettings(
            final IdentityAdapterSettings settings) {
        return AuthorizationServerSettings.builder()
                .issuer(settings.publicUrl().toString().replaceAll("/+$", ""))
                .build();
    }

    @Bean
    SignInAuthorizations authorizationService() {
        return new SignInAuthorizations();
    }

    /** The web client: a public client using the authorization code flow with PKCE. */
    @Bean
    RegisteredClientRepository registeredClientRepository(final IdentityAdapterSettings settings) {
        final IdentityAdapterSettings.WebClient web = settings.webClient();
        final RegisteredClient client =
                RegisteredClient.withId(web.clientId())
                        .clientId(web.clientId())
                        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUris(
                                uris -> web.redirectUris().forEach(u -> uris.add(u.toString())))
                        .postLogoutRedirectUri(web.signInUrl().toString())
                        .scope(OidcScopes.OPENID)
                        .clientSettings(
                                ClientSettings.builder()
                                        .requireProofKey(true)
                                        .requireAuthorizationConsent(false)
                                        .build())
                        .tokenSettings(
                                TokenSettings.builder()
                                        .accessTokenTimeToLive(ACCESS_TOKEN_TIME_TO_LIVE)
                                        .refreshTokenTimeToLive(REFRESH_TOKEN_TIME_TO_LIVE)
                                        .reuseRefreshTokens(false)
                                        .build())
                        .build();
        return new WebClientRegistrations(client);
    }

    @Bean
    OAuth2TokenGenerator<OAuth2Token> tokenGenerator(
            final JWKSource<SecurityContext> jwkSource, final Clock clock) {
        final JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwtGenerator.setJwtCustomizer(new AccessTokenCustomizer());
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator, new PublicClientRefreshTokenGenerator(clock));
    }

    /** The authorization server endpoints: {@code /oauth2/*}, {@code /.well-known/*} and OIDC. */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerFilterChain(
            final HttpSecurity http,
            final IdentityAdapterSettings settings,
            final AuthorizationServerSettings serverSettings,
            final RegisteredClientRepository clients,
            final OAuth2AuthorizationService authorizations,
            final OAuth2TokenGenerator<OAuth2Token> tokenGenerator)
            throws Exception {
        http.oauth2AuthorizationServer(
                        server -> {
                            http.securityMatcher(server.getEndpointsMatcher());
                            server.registeredClientRepository(clients)
                                    .authorizationService(authorizations)
                                    .tokenGenerator(tokenGenerator)
                                    .clientAuthentication(authentication ->
                                            authentication
                                                    .authenticationConverter(
                                                            new PublicClientRefreshTokenAuthenticationConverter(
                                                                    serverSettings
                                                                            .getTokenEndpoint()))
                                                    .authenticationProvider(
                                                            new PublicClientRefreshTokenAuthenticationProvider(
                                                                    clients)))
                                    .oidc(oidc ->
                                            oidc.logoutEndpoint(logout ->
                                                    logout.logoutResponseHandler(
                                                            new EndAuthorizationOnSignOut(
                                                                    authorizations))));
                        })
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                // The OIDC user info endpoint takes Dulo's own access tokens.
                .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()))
                // Browsers that start a sign-in go to an identity provider; API calls get a 401.
                .exceptionHandling(exceptions ->
                        exceptions.defaultAuthenticationEntryPointFor(
                                new SignInEntryPoint(settings),
                                PathPatternRequestMatcher.withDefaults()
                                        .matcher(serverSettings.getAuthorizationEndpoint())))
                .cors(Customizer.withDefaults());
        return http.build();
    }
}
