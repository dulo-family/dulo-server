package com.dulo.adapter.identity.authserver;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Map;

/**
 * Recognizes a public client's refresh token request: {@code grant_type=refresh_token} with a
 * {@code client_id} and no client credentials. Spring's public client converter requires a PKCE
 * code verifier, which only the authorization code request has.
 *
 * <p>It only looks at the token endpoint. Spring runs client authentication on the introspection
 * and revocation endpoints too, and a {@code grant_type} parameter there must not authenticate the
 * caller as the client.
 */
class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter {

    private final RequestMatcher tokenEndpoint;

    PublicClientRefreshTokenAuthenticationConverter(final String tokenEndpoint) {
        this.tokenEndpoint =
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, tokenEndpoint);
    }

    @Override
    public @Nullable Authentication convert(final HttpServletRequest request) {
        if (!this.tokenEndpoint.matches(request)
                || !AuthorizationGrantType.REFRESH_TOKEN
                        .getValue()
                        .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                || request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null
                || request.getParameter(OAuth2ParameterNames.CLIENT_ASSERTION) != null) {
            return null;
        }
        final String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        if (clientIds == null) {
            return null;
        }
        if (clientIds.length != 1 || clientIds[0].isBlank()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        return new OAuth2ClientAuthenticationToken(
                clientIds[0],
                ClientAuthenticationMethod.NONE,
                null,
                Map.of(
                        OAuth2ParameterNames.GRANT_TYPE,
                        AuthorizationGrantType.REFRESH_TOKEN.getValue()));
    }
}
