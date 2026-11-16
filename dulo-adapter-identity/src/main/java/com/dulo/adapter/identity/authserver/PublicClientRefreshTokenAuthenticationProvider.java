package com.dulo.adapter.identity.authserver;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Authenticates a public client for the refresh token grant by its client id alone. The refresh
 * token itself is the proof: it is long, bound to this client, and replaced on every use.
 */
class PublicClientRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository clients;

    PublicClientRefreshTokenAuthenticationProvider(final RegisteredClientRepository clients) {
        this.clients = clients;
    }

    @Override
    public @Nullable Authentication authenticate(final Authentication authentication) {
        final OAuth2ClientAuthenticationToken token =
                (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(token.getClientAuthenticationMethod())
                || !AuthorizationGrantType.REFRESH_TOKEN
                        .getValue()
                        .equals(
                                token.getAdditionalParameters()
                                        .get(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        final RegisteredClient client =
                this.clients.findByClientId(token.getPrincipal().toString());
        if (client == null
                || !client.getClientAuthenticationMethods()
                        .contains(ClientAuthenticationMethod.NONE)
                || !client.getAuthorizationGrantTypes()
                        .contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(final Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
