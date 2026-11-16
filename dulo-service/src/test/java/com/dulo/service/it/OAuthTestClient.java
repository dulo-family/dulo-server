package com.dulo.service.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Signs in like a browser and the web client together: the authorization code flow with PKCE
 * against Dulo, which sends the browser through the mock identity provider's login form. Each
 * instance has its own cookies, like a separate browser.
 */
final class OAuthTestClient {

    static final String CLIENT_ID = "dulo-web";
    static final String REDIRECT_URI = "http://127.0.0.1/callback";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HttpClient http =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .cookieHandler(new CookieManager())
                    .build();
    private final String baseUrl;

    OAuthTestClient(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private static String form(final Map<String, String> values) {
        return values.entrySet().stream()
                .map(e ->
                        URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                + "="
                                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String randomString() {
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challenge(final String verifier) {
        try {
            final byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String queryParameter(final URI uri, final String name) {
        for (final String pair : uri.getRawQuery().split("&")) {
            final String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("No " + name + " in " + uri);
    }

    private static Tokens tokens(final HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        final JsonNode body = JSON.readTree(response.body());
        return new Tokens(
                body.path("access_token").asString(),
                body.path("refresh_token").asString(),
                body.path("expires_in").asLong());
    }

    /** The authorization request URL, with an optional identity provider hint. */
    String authorizationUrl(final String challenge, final @Nullable String idp) {
        final Map<String, String> query = new LinkedHashMap<>();
        query.put("response_type", "code");
        query.put("client_id", CLIENT_ID);
        query.put("redirect_uri", REDIRECT_URI);
        query.put("scope", "openid");
        query.put("state", "test-state");
        query.put("code_challenge", challenge);
        query.put("code_challenge_method", "S256");
        if (idp != null) {
            query.put("idp", idp);
        }
        return this.baseUrl + "/oauth2/authorize?" + form(query);
    }

    /**
     * Signs in at the mock identity provider as {@code username} with the given extra ID token
     * claims, and exchanges the code for tokens.
     */
    Tokens signIn(final String username, final String claimsJson)
            throws IOException, InterruptedException {
        final String verifier = randomString();

        // Dulo has a single identity provider, so it skips the sign-in page.
        final URI toProvider =
                this.redirect(this.get(this.authorizationUrl(challenge(verifier), null)));
        assertThat(toProvider.getPath()).isEqualTo("/oauth2/authorization/mock");
        final URI providerLogin = this.redirect(this.get(toProvider.toString()));

        // The mock provider's login form.
        final URI callback =
                this.redirect(
                        this.post(
                                providerLogin.toString(),
                                form(Map.of("username", username, "claims", claimsJson))));
        assertThat(callback.getPath()).isEqualTo("/login/oauth2/code/mock");

        // Back at Dulo: the sign-in completes and the original authorization request resumes.
        final URI resumed = this.redirect(this.get(callback.toString()));
        assertThat(resumed.getPath()).isEqualTo("/oauth2/authorize");
        final URI clientRedirect = this.redirect(this.get(resumed.toString()));
        assertThat(clientRedirect.toString()).startsWith(REDIRECT_URI);
        assertThat(queryParameter(clientRedirect, "state")).isEqualTo("test-state");

        return this.tokens(
                Map.of(
                        "grant_type", "authorization_code",
                        "code", queryParameter(clientRedirect, "code"),
                        "redirect_uri", REDIRECT_URI,
                        "client_id", CLIENT_ID,
                        "code_verifier", verifier));
    }

    Tokens refresh(final String refreshToken) throws IOException, InterruptedException {
        return this.tokens(
                Map.of(
                        "grant_type", "refresh_token",
                        "refresh_token", refreshToken,
                        "client_id", CLIENT_ID));
    }

    /** Refreshes the way Postman does: the client id in a Basic header with no password. */
    Tokens refreshWithBasicHeader(final String refreshToken)
            throws IOException, InterruptedException {
        final String credentials =
                Base64.getEncoder()
                        .encodeToString((CLIENT_ID + ":").getBytes(StandardCharsets.UTF_8));
        final String form =
                form(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken));
        return tokens(
                this.http.send(
                        HttpRequest.newBuilder(URI.create(this.baseUrl + "/oauth2/token"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .header("Authorization", "Basic " + credentials)
                                .POST(HttpRequest.BodyPublishers.ofString(form))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()));
    }

    HttpResponse<String> tokenRequest(final Map<String, String> parameters)
            throws IOException, InterruptedException {
        return this.post(this.baseUrl + "/oauth2/token", form(parameters));
    }

    HttpResponse<String> get(final String url) throws IOException, InterruptedException {
        return this.http.send(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Tokens tokens(final Map<String, String> parameters)
            throws IOException, InterruptedException {
        return tokens(this.tokenRequest(parameters));
    }

    private HttpResponse<String> post(final String url, final String form)
            throws IOException, InterruptedException {
        return this.http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI redirect(final HttpResponse<String> response) {
        assertThat(response.statusCode())
                .as("redirect from %s: %s", response.uri(), response.body())
                .isEqualTo(302);
        final URI location = URI.create(response.headers().firstValue("Location").orElseThrow());
        return location.isAbsolute() ? location : response.uri().resolve(location);
    }

    record Tokens(String accessToken, String refreshToken, long expiresIn) {}
}
