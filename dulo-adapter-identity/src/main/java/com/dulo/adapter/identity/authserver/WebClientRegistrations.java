package com.dulo.adapter.identity.authserver;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** The clients of Dulo's authorization server, which come from the configuration. */
class WebClientRegistrations implements RegisteredClientRepository {

    private final RegisteredClient client;

    WebClientRegistrations(final RegisteredClient client) {
        this.client = client;
    }

    @Override
    public void save(final RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Clients come from the configuration");
    }

    @Override
    public @Nullable RegisteredClient findById(final String id) {
        return this.client.getId().equals(id) ? this.client : null;
    }

    @Override
    public @Nullable RegisteredClient findByClientId(final String clientId) {
        return this.client.getClientId().equals(clientId) ? this.client : null;
    }
}
