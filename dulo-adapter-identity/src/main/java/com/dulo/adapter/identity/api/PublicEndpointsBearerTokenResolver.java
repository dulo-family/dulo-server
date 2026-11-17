package com.dulo.adapter.identity.api;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Ignores the access token on the public endpoints. Otherwise an expired token, which a client may
 * still send, gets 401 on endpoints that need no token at all, such as the identity providers the
 * sign-in page lists.
 */
final class PublicEndpointsBearerTokenResolver implements BearerTokenResolver {

    private final RequestMatcher publicEndpoints;
    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    PublicEndpointsBearerTokenResolver(final RequestMatcher publicEndpoints) {
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    public @Nullable String resolve(final HttpServletRequest request) {
        return this.publicEndpoints.matches(request) ? null : this.delegate.resolve(request);
    }
}
