package com.dulo.adapter.identity.settings;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the identity adapter needs from the configuration. The service module maps the Dulo
 * configuration into it, so this adapter does not depend on the configuration classes.
 *
 * @param publicUrl the server's public base URL; the token issuer and the base of callback URLs
 * @param providers the upstream identity providers users sign in with
 * @param webClient the Dulo web client, a public OAuth2 client of Dulo
 */
public record IdentityAdapterSettings(
        URI publicUrl, List<Provider> providers, WebClient webClient) {

    public IdentityAdapterSettings {
        Objects.requireNonNull(publicUrl, "publicUrl");
        providers = List.copyOf(providers);
        Objects.requireNonNull(webClient, "webClient");
    }

    public Optional<Provider> provider(final String id) {
        return providers.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** An absolute URL on this server. It may hold URI template variables, so it is a string. */
    public String url(final String path) {
        return publicUrl.toString().replaceAll("/+$", "") + path;
    }

    public enum ProviderType {
        OIDC,
        GOOGLE,
        MICROSOFT,
        ;
    }

    /**
     * An upstream identity provider.
     *
     * @param issuerUri the OIDC issuer; required except for Google
     * @param clientSecret null for a public client, which then uses PKCE only
     */
    public record Provider(
            String id,
            ProviderType type,
            String displayName,
            @Nullable URI issuerUri,
            String clientId,
            @Nullable String clientSecret,
            List<String> scopes,
            Claims claims) {

        public Provider {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(clientId, "clientId");
            scopes = List.copyOf(scopes);
            Objects.requireNonNull(claims, "claims");
        }
    }

    /** Names of the claims Dulo reads from the provider's ID token. */
    public record Claims(String roles, String email, String name) {}

    /**
     * The Dulo web client.
     *
     * @param redirectUris where the authorization server may send the code; at least one
     * @param signInUrl the web client's sign-in page, which lists the identity providers
     */
    public record WebClient(
            String clientId, List<URI> redirectUris, List<String> allowedOrigins, URI signInUrl) {

        public WebClient {
            Objects.requireNonNull(clientId, "clientId");
            redirectUris = List.copyOf(redirectUris);
            if (redirectUris.isEmpty()) {
                throw new IllegalArgumentException("The web client needs a redirect URI");
            }
            allowedOrigins = List.copyOf(allowedOrigins);
            Objects.requireNonNull(signInUrl, "signInUrl");
        }
    }
}
