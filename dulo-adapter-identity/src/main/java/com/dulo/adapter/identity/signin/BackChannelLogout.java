package com.dulo.adapter.identity.signin;

import com.dulo.adapter.identity.authserver.SignInAuthorizations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Signs a user out of Dulo when their identity provider reports that they signed out there (OpenID
 * Connect Back-Channel Logout, {@code POST /logout/connect/back-channel/{providerId}}). Spring
 * checks the logout token first; this then ends the browser sessions of that sign-in, so the next
 * authorization request goes to the provider again, and removes the authorizations that came from
 * it, so the clients' refresh tokens stop working. Access tokens already issued stay valid until
 * they expire.
 *
 * <p>A logout token with {@code sid} ends that provider session; one with only {@code sub} ends all
 * of the user's sessions at that provider. This matches Spring's {@code OidcSessionRegistry}.
 */
final class BackChannelLogout implements LogoutHandler {

    private static final System.Logger LOG = System.getLogger(BackChannelLogout.class.getName());

    private final OidcSessionRegistry sessionRegistry;
    private final BrowserSessions browserSessions;
    private final SignInAuthorizations authorizations;

    BackChannelLogout(
            final OidcSessionRegistry sessionRegistry,
            final BrowserSessions browserSessions,
            final SignInAuthorizations authorizations) {
        this.sessionRegistry = sessionRegistry;
        this.browserSessions = browserSessions;
        this.authorizations = authorizations;
    }

    /** Whether the provider ID token belongs to the sign-in the logout token ends. */
    static boolean endedBy(final OidcLogoutToken logoutToken, final OidcIdToken idToken) {
        final List<String> audience = Optional.ofNullable(idToken.getAudience()).orElse(List.of());
        if (Collections.disjoint(logoutToken.getAudience(), audience)
                || !issuer(logoutToken.getIssuer()).equals(issuer(idToken.getIssuer()))) {
            return false;
        }
        return Optional.ofNullable(logoutToken.getSessionId())
                .map(sid -> sid.equals(idToken.getClaimAsString("sid")))
                .orElseGet(() ->
                        Optional.ofNullable(logoutToken.getSubject())
                                .map(sub -> sub.equals(idToken.getSubject()))
                                .orElse(false));
    }

    /** As text: {@link URL#equals} resolves host names. */
    private static String issuer(final @Nullable URL issuer) {
        return Optional.ofNullable(issuer).map(URL::toExternalForm).orElse("");
    }

    @Override
    public void logout(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof final OidcLogoutToken logoutToken)) {
            return;
        }
        int sessions = 0;
        for (final OidcSessionInformation session :
                this.sessionRegistry.removeSessionInformation(logoutToken)) {
            this.browserSessions.invalidate(session.getSessionId());
            sessions++;
        }
        final int signIns =
                this.authorizations.removeSignedInWith(idToken -> endedBy(logoutToken, idToken));
        LOG.log(
                System.Logger.Level.INFO,
                "Back-channel logout from {0}: ended {1} browser sessions and {2} authorizations",
                issuer(logoutToken.getIssuer()),
                sessions,
                signIns);
    }
}
