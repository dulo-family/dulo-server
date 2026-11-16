package com.dulo.adapter.identity.authserver;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.Provider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.Optional;

/**
 * Sends a user who is not signed in to Dulo yet to an identity provider. It skips the web client's
 * sign-in page when the choice is clear: only one provider is configured, or the authorization
 * request names one with the {@code idp} parameter. Tools like Postman then work without the web
 * client.
 */
class SignInEntryPoint implements AuthenticationEntryPoint {

    /** Authorization request parameter naming the identity provider to sign in with. */
    static final String IDP_PARAMETER = "idp";

    private final IdentityAdapterSettings settings;

    SignInEntryPoint(final IdentityAdapterSettings settings) {
        this.settings = settings;
    }

    @Override
    public void commence(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AuthenticationException authException)
            throws IOException {
        response.sendRedirect(this.target(request));
    }

    private String target(final HttpServletRequest request) {
        return this.chosenProvider(request)
                .map(p -> this.settings.url("/oauth2/authorization/" + p.id()))
                .orElseGet(() -> this.settings.webClient().signInUrl().toString());
    }

    private Optional<Provider> chosenProvider(final HttpServletRequest request) {
        final String hint = request.getParameter(IDP_PARAMETER);
        if (hint != null) {
            return this.settings.provider(hint);
        }
        return this.settings.providers().size() == 1
                ? Optional.of(this.settings.providers().getFirst())
                : Optional.empty();
    }
}
