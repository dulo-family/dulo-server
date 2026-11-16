package com.dulo.adapter.identity.signin;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.core.identity.port.in.SignInUseCase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sign-in through the upstream identity providers. {@code /oauth2/authorization/{id}} sends the
 * browser to the provider, and {@code /login/oauth2/code/{id}} receives it back. After a successful
 * sign-in the browser returns to the authorization request it started with.
 */
@Configuration(proxyBeanMethods = false)
public class SignInConfiguration {

    static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";
    static final String ERROR_PARAMETER = "?error=sign-in-failed";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            final IdentityAdapterSettings settings) {
        return new ProviderClientRegistrations(settings);
    }

    @Bean
    @Order(2)
    SecurityFilterChain signInFilterChain(
            final HttpSecurity http,
            final IdentityAdapterSettings settings,
            final ClientRegistrationRepository registrations,
            final SignInUseCase signIn)
            throws Exception {
        final DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        registrations, AUTHORIZATION_BASE_URI);
        // PKCE for confidential clients too.
        resolver.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
        final String failureUrl = settings.webClient().signInUrl() + ERROR_PARAMETER;

        http.securityMatcher(AUTHORIZATION_BASE_URI + "/**", "/login/oauth2/code/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .oauth2Login(login ->
                        login.clientRegistrationRepository(registrations)
                                .authorizationEndpoint(
                                        endpoint -> endpoint.authorizationRequestResolver(resolver))
                                .userInfoEndpoint(userInfo ->
                                        userInfo.oidcUserService(
                                                new DuloOidcUserService(settings, signIn)))
                                .failureUrl(failureUrl));
        return http.build();
    }
}
