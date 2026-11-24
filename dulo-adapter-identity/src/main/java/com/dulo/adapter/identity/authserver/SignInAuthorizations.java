package com.dulo.adapter.identity.authserver;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Dulo's authorizations, with their codes and tokens, in memory: a restart signs everyone out. Each
 * one remembers the identity provider sign-in it came from (the provider's ID token), so the
 * authorizations of a sign-in the provider has ended can be removed, and their refresh tokens stop
 * working.
 */
public final class SignInAuthorizations implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService authorizations =
            new InMemoryOAuth2AuthorizationService();
    private final Set<String> ids = ConcurrentHashMap.newKeySet();

    /** The provider's ID token of the sign-in an authorization came from, if any. */
    private static Optional<OidcIdToken> providerIdToken(final OAuth2Authorization authorization) {
        final Authentication principal = authorization.getAttribute(Principal.class.getName());
        return Optional.ofNullable(principal)
                .map(Authentication::getPrincipal)
                .filter(OidcUser.class::isInstance)
                .map(user -> ((OidcUser) user).getIdToken());
    }

    @Override
    public void save(final OAuth2Authorization authorization) {
        this.authorizations.save(authorization);
        this.ids.add(authorization.getId());
    }

    @Override
    public void remove(final OAuth2Authorization authorization) {
        this.authorizations.remove(authorization);
        this.ids.remove(authorization.getId());
    }

    @Override
    public @Nullable OAuth2Authorization findById(final String id) {
        return this.authorizations.findById(id);
    }

    @Override
    public @Nullable OAuth2Authorization findByToken(
            final String token, final @Nullable OAuth2TokenType tokenType) {
        return this.authorizations.findByToken(token, tokenType);
    }

    /**
     * Removes the authorizations that came from a sign-in whose provider ID token matches.
     *
     * @return how many were removed
     */
    public int removeSignedInWith(final Predicate<OidcIdToken> providerIdToken) {
        int removed = 0;
        for (final String id : this.ids) {
            final OAuth2Authorization authorization = this.authorizations.findById(id);
            if (authorization == null) {
                // Dropped by the in-memory store, e.g. an authorization request never completed.
                this.ids.remove(id);
            } else if (providerIdToken(authorization).filter(providerIdToken).isPresent()) {
                this.remove(authorization);
                removed++;
            }
        }
        return removed;
    }
}
