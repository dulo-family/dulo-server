package com.dulo.adapter.identity.settings;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings.Claims;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.Provider;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.ProviderType;
import com.dulo.adapter.identity.settings.IdentityAdapterSettings.WebClient;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;

/** Settings for unit tests. */
public final class TestSettings {

    public static final URI SIGN_IN_URL = URI.create("https://tree.example.com/sign-in");

    private TestSettings() {}

    public static Provider provider(final String id, final @Nullable String secret) {
        return new Provider(
                id,
                ProviderType.GOOGLE,
                "Provider " + id,
                null,
                "client-" + id,
                secret,
                List.of("profile", "email"),
                new Claims("groups", "email", "name"));
    }

    public static IdentityAdapterSettings settings(final Provider... providers) {
        return new IdentityAdapterSettings(
                URI.create("https://tree.example.com/"),
                List.of(providers),
                new WebClient(
                        "dulo-web",
                        List.of(URI.create("https://tree.example.com/auth/callback")),
                        List.of("https://tree.example.com"),
                        SIGN_IN_URL));
    }
}
