package com.dulo.adapter.identity.authserver;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * Issues refresh tokens to every client, public clients included. Spring's own generator skips
 * public clients, but the web client is one and needs to stay signed in for longer than an access
 * token lives. Refresh tokens are rotated on every use, as OAuth 2.1 asks for public clients.
 */
class PublicClientRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private static final int KEY_LENGTH = 96;

    private final StringKeyGenerator keys =
            new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), KEY_LENGTH);
    private final Clock clock;

    PublicClientRefreshTokenGenerator(final Clock clock) {
        this.clock = clock;
    }

    @Override
    public @Nullable OAuth2RefreshToken generate(final OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            return null;
        }
        final Instant issuedAt = this.clock.instant();
        final Instant expiresAt =
                issuedAt.plus(
                        context.getRegisteredClient()
                                .getTokenSettings()
                                .getRefreshTokenTimeToLive());
        return new OAuth2RefreshToken(this.keys.generateKey(), issuedAt, expiresAt);
    }
}
