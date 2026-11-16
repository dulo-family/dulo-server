package com.dulo.adapter.identity.signin;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.Claims;
import com.dulo.core.identity.port.in.ExternalSignIn;
import com.dulo.core.identity.port.in.SignInUseCase;
import com.dulo.domain.user.ExternalIdentity;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the user from the identity provider, then turns them into a Dulo user through {@link
 * SignInUseCase}: it creates the user on the first sign-in and maps the roles claim.
 */
class DuloOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final IdentityAdapterSettings settings;
    private final SignInUseCase signIn;

    DuloOidcUserService(final IdentityAdapterSettings settings, final SignInUseCase signIn) {
        this(new OidcUserService(), settings, signIn);
    }

    DuloOidcUserService(
            final OAuth2UserService<OidcUserRequest, OidcUser> delegate,
            final IdentityAdapterSettings settings,
            final SignInUseCase signIn) {
        this.delegate = delegate;
        this.settings = settings;
        this.signIn = signIn;
    }

    /** Claim values as strings: a list, or a single value split on commas and whitespace. */
    static Set<String> claimValues(final @Nullable Object claim) {
        if (claim instanceof final Collection<?> values) {
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }
        if (claim instanceof final String value) {
            return Arrays.stream(value.split("[,\\s]+"))
                    .filter(v -> !v.isEmpty())
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    @Override
    public OidcUser loadUser(final OidcUserRequest request) {
        final OidcUser user = this.delegate.loadUser(request);
        final String providerId = request.getClientRegistration().getRegistrationId();
        final Claims claims =
                this.settings
                        .provider(providerId)
                        .orElseThrow(() ->
                                new OAuth2AuthenticationException(
                                        new OAuth2Error(
                                                "unknown_provider",
                                                "Unknown identity provider " + providerId,
                                                null)))
                        .claims();
        final UserId userId =
                this.signIn.signIn(
                        new ExternalSignIn(
                                new ExternalIdentity(providerId, user.getSubject()),
                                user.getClaimAsString(claims.email()),
                                user.getClaimAsString(claims.name()),
                                claimValues(user.getClaims().get(claims.roles()))));
        return new DuloOidcUser(user, userId);
    }
}
