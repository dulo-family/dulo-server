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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Recognizes a public client's refresh token request: {@code grant_type=refresh_token} with a
 * client id and no client secret. Spring's public client converter requires a PKCE code verifier,
 * which only the authorization code request has.
 *
 * <p>The client id comes in the {@code client_id} parameter, or in an HTTP Basic header with an
 * empty password, which some tools send when refreshing (Postman does). An empty {@code
 * client_secret} parameter counts as none. Either way the request proves no more than the client
 * id, so the provider only accepts clients that authenticate with {@code none}.
 *
 * <p>It only looks at the token endpoint. Spring runs client authentication on the introspection
 * and revocation endpoints too, and a {@code grant_type} parameter there must not authenticate the
 * caller as the client.
 */
class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter {

    private static final String BASIC = "Basic ";

    private final RequestMatcher tokenEndpoint;

    PublicClientRefreshTokenAuthenticationConverter(final String tokenEndpoint) {
        this.tokenEndpoint =
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, tokenEndpoint);
    }

    private static OAuth2AuthenticationException invalidRequest() {
        return new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
    }

    /** The {@code client_id} parameter, or null without one. */
    private static @Nullable String parameterClientId(final HttpServletRequest request) {
        final String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        if (clientIds == null) {
            return null;
        }
        if (clientIds.length != 1 || clientIds[0].isBlank()) {
            throw invalidRequest();
        }
        return clientIds[0];
    }

    /**
     * The client id of an HTTP Basic header with an empty password, or null for any other header,
     * which Spring's converters handle.
     */
    private static @Nullable String basicClientIdWithoutSecret(final String header) {
        if (!header.regionMatches(true, 0, BASIC, 0, BASIC.length())) {
            return null;
        }
        try {
            final String credentials =
                    new String(
                            Base64.getDecoder().decode(header.substring(BASIC.length()).trim()),
                            StandardCharsets.UTF_8);
            final int colon = credentials.indexOf(':');
            if (colon <= 0 || colon != credentials.length() - 1) {
                return null;
            }
            // RFC 6749 section 2.3.1: the client id is form-encoded before Base64.
            return URLDecoder.decode(credentials.substring(0, colon), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException _) {
            return null;
        }
    }

    @Override
    public @Nullable Authentication convert(final HttpServletRequest request) {
        final String secret = request.getParameter(OAuth2ParameterNames.CLIENT_SECRET);
        if (!this.tokenEndpoint.matches(request)
                || !AuthorizationGrantType.REFRESH_TOKEN
                        .getValue()
                        .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                || (secret != null && !secret.isEmpty())
                || request.getParameter(OAuth2ParameterNames.CLIENT_ASSERTION) != null) {
            return null;
        }
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        final String headerClientId = header == null ? null : basicClientIdWithoutSecret(header);
        if (header != null && headerClientId == null) {
            return null;
        }
        final String parameterClientId = parameterClientId(request);
        if (headerClientId != null
                && parameterClientId != null
                && !headerClientId.equals(parameterClientId)) {
            throw invalidRequest();
        }
        final String clientId = headerClientId != null ? headerClientId : parameterClientId;
        if (clientId == null) {
            return null;
        }
        return new OAuth2ClientAuthenticationToken(
                clientId,
                ClientAuthenticationMethod.NONE,
                null,
                Map.of(
                        OAuth2ParameterNames.GRANT_TYPE,
                        AuthorizationGrantType.REFRESH_TOKEN.getValue()));
    }
}
