package com.dulo.adapter.identity.signin;

import com.dulo.domain.user.UserId;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A user signed in through an identity provider, named by their Dulo user id. The authorization
 * server uses the name as the subject of the tokens it issues.
 */
final class DuloOidcUser extends DefaultOidcUser {

    private final UserId userId;

    DuloOidcUser(final OidcUser user, final UserId userId) {
        super(authorities(user), user.getIdToken(), user.getUserInfo());
        this.userId = userId;
    }

    /**
     * The user's authorities plus the factor "signed in with an authorization code". The
     * authorization server reads the sign-in time from it for the ID token's {@code auth_time}.
     * Spring's plain OAuth2 login adds it, but its OIDC login does not (Spring Security 7.1).
     */
    private static Set<GrantedAuthority> authorities(final OidcUser user) {
        final Set<GrantedAuthority> authorities = new LinkedHashSet<>(user.getAuthorities());
        if (authorities.stream().noneMatch(FactorGrantedAuthority.class::isInstance)) {
            authorities.add(
                    FactorGrantedAuthority.fromAuthority(
                            FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY));
        }
        return authorities;
    }

    @Override
    public String getName() {
        return this.userId.toString();
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof final DuloOidcUser user
                && this.userId.equals(user.userId)
                && super.equals(other);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + this.userId.hashCode();
    }
}
