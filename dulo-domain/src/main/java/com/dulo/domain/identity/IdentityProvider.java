package com.dulo.domain.identity;

import java.util.Objects;

/**
 * An identity provider users can sign in with, as shown on the sign-in page.
 *
 * @param id the provider's id in the configuration, e.g. {@code google}
 * @param displayName the name on the sign-in button
 */
public record IdentityProvider(String id, String displayName, IdentityProviderType type) {

    public IdentityProvider {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(type, "type");
    }
}
