package com.dulo.adapter.identity.authserver;

import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;

/**
 * Makes access tokens JWT access tokens as RFC 9068 defines them: the {@code at+jwt} type, a {@code
 * client_id} claim, and Dulo itself, the resource server, as the audience. ID tokens have the same
 * issuer, subject and signing key, but the client as the audience, so the API tells them apart by
 * these and rejects ID tokens.
 */
class AccessTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    static final String TYPE = "at+jwt";
    static final String CLIENT_ID = "client_id";

    @Override
    public void customize(final JwtEncodingContext context) {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getJwsHeader().type(TYPE);
            context.getClaims()
                    .audience(List.of(context.getAuthorizationServerContext().getIssuer()))
                    .claim(CLIENT_ID, context.getRegisteredClient().getClientId());
        }
    }
}
