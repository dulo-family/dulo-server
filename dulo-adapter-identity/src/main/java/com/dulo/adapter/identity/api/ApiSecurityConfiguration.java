package com.dulo.adapter.identity.api;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Protects the REST API: every call needs one of Dulo's access tokens, except the few public
 * endpoints the sign-in and setup pages need. Which user may do what is decided in the core.
 */
@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfiguration {

    /**
     * Endpoints that need no token: the OpenAPI document, the identity providers, and the setup
     * steps that need no signed-in user (the status, and what the setup token protects). Completing
     * the setup needs a signed-in user, who becomes root. A token sent to these endpoints is
     * ignored.
     */
    static final RequestMatcher PUBLIC_ENDPOINTS =
            new OrRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher("/api/v1/openapi.json"),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/v1/identity-providers"),
                    PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.GET, "/api/v1/setup/**"));

    /** Headers browsers may send to the API and the token endpoint. */
    private static final List<String> ALLOWED_HEADERS =
            List.of(
                    HttpHeaders.AUTHORIZATION,
                    HttpHeaders.CONTENT_TYPE,
                    HttpHeaders.IF_MATCH,
                    "X-Dulo-Setup-Token");

    /** Lets the web client call the API and the token endpoints from its own origin. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(final IdentityAdapterSettings settings) {
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        final List<String> origins = settings.webClient().allowedOrigins();
        if (origins.isEmpty()) {
            return source;
        }
        final CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(
                List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name()));
        cors.setAllowedHeaders(ALLOWED_HEADERS);
        cors.setExposedHeaders(List.of(HttpHeaders.ETAG, HttpHeaders.LOCATION));
        cors.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", cors);
        source.registerCorsConfiguration("/oauth2/**", cors);
        source.registerCorsConfiguration("/.well-known/**", cors);
        source.registerCorsConfiguration("/userinfo", cors);
        source.registerCorsConfiguration("/connect/**", cors);
        return source;
    }

    @Bean
    @Order(3)
    SecurityFilterChain apiFilterChain(final HttpSecurity http) throws Exception {
        final ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint();
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(requests ->
                        requests.requestMatchers(PUBLIC_ENDPOINTS)
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(server ->
                        server.jwt(Customizer.withDefaults())
                                .bearerTokenResolver(
                                        new PublicEndpointsBearerTokenResolver(PUBLIC_ENDPOINTS))
                                .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .sessionManagement(
                        sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens are not sent automatically by browsers, so CSRF cannot happen.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults());
        return http.build();
    }

    /** Everything else: health checks are public, the rest does not exist. */
    @Bean
    @Order(4)
    SecurityFilterChain defaultFilterChain(final HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests ->
                        requests.requestMatchers("/actuator/health/**", "/error")
                                .permitAll()
                                .anyRequest()
                                .denyAll())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
