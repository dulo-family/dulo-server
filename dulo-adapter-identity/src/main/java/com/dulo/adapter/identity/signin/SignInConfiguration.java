package com.dulo.adapter.identity.signin;

import com.dulo.adapter.identity.authserver.SignInAuthorizations;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.core.identity.port.in.SignInUseCase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.session.InMemoryOidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Sign-in through the upstream identity providers. {@code /oauth2/authorization/{id}} sends the
 * browser to the provider, and {@code /login/oauth2/code/{id}} receives it back. After a successful
 * sign-in the browser returns to the authorization request it started with. Providers report
 * sign-outs to {@code /logout/connect/back-channel/{id}} (see {@link BackChannelLogout}).
 */
@Configuration(proxyBeanMethods = false)
public class SignInConfiguration {

    static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";
    static final String ERROR_PARAMETER = "?error=sign-in-failed";
    static final String BACK_CHANNEL_LOGOUT_URI = "/logout/connect/back-channel";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            final IdentityAdapterSettings settings) {
        return new ProviderClientRegistrations(settings);
    }

    /** Which browser session each provider sign-in belongs to. */
    @Bean
    OidcSessionRegistry oidcSessionRegistry() {
        return new InMemoryOidcSessionRegistry();
    }

    @Bean
    BrowserSessions browserSessions() {
        return new BrowserSessions();
    }

    /** Session events for Spring Security, which keep the {@link OidcSessionRegistry} current. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    @Order(2)
    SecurityFilterChain signInFilterChain(
            final HttpSecurity http,
            final IdentityAdapterSettings settings,
            final ClientRegistrationRepository registrations,
            final SignInUseCase signIn,
            final OidcSessionRegistry sessionRegistry,
            final BrowserSessions browserSessions,
            final SignInAuthorizations authorizations)
            throws Exception {
        final DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        registrations, AUTHORIZATION_BASE_URI);
        // PKCE for confidential clients too.
        resolver.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
        final String failureUrl = settings.webClient().signInUrl() + ERROR_PARAMETER;

        http.securityMatcher(
                        AUTHORIZATION_BASE_URI + "/**",
                        "/login/oauth2/code/**",
                        BACK_CHANNEL_LOGOUT_URI + "/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .oauth2Login(login ->
                        login.clientRegistrationRepository(registrations)
                                .authorizationEndpoint(
                                        endpoint -> endpoint.authorizationRequestResolver(resolver))
                                .userInfoEndpoint(userInfo ->
                                        userInfo.oidcUserService(
                                                new DuloOidcUserService(settings, signIn)))
                                .failureUrl(failureUrl))
                .oidcLogout(logout ->
                        logout.clientRegistrationRepository(registrations)
                                .oidcSessionRegistry(sessionRegistry)
                                .backChannel(backChannel ->
                                        backChannel.logoutHandler(
                                                new BackChannelLogout(
                                                        sessionRegistry,
                                                        browserSessions,
                                                        authorizations))));
        return http.build();
    }
}
