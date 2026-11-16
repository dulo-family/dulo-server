package com.dulo.domain.identity;

import java.util.Locale;

/** The kind of identity provider, which decides how Dulo talks to it. */
public enum IdentityProviderType {
    /** Any OpenID Connect provider, found through its issuer URL. */
    OIDC,
    GOOGLE,
    MICROSOFT,
    ;

    /** Stable external name, e.g. {@code google}. */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
