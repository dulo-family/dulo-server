package com.dulo.adapter.identity.authserver;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Optional;

/**
 * Ends the sign-in a client signs out of. The end-session endpoint ({@code /connect/logout}) finds
 * the authorization that issued the ID token in {@code id_token_hint}; this removes it, so its
 * refresh token stops working, and then ends the browser session and redirects as Spring does. The
 * user's other sign-ins (other browsers and devices) stay. Access tokens already issued stay valid
 * until they expire.
 */
final class EndAuthorizationOnSignOut implements AuthenticationSuccessHandler {

    private static final OAuth2TokenType ID_TOKEN =
            new OAuth2TokenType(OidcParameterNames.ID_TOKEN);

    private final OAuth2AuthorizationService authorizations;
    private final AuthenticationSuccessHandler then = new OidcLogoutAuthenticationSuccessHandler();

    EndAuthorizationOnSignOut(final OAuth2AuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    @Override
    public void onAuthenticationSuccess(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Authentication authentication)
            throws IOException, ServletException {
        if (authentication instanceof final OidcLogoutAuthenticationToken signOut) {
            // The ID token's value; unlike getIdToken(), never null.
            Optional.ofNullable(this.authorizations.findByToken(signOut.getIdTokenHint(), ID_TOKEN))
                    .ifPresent(this.authorizations::remove);
        }
        this.then.onAuthenticationSuccess(request, response, authentication);
    }
}
