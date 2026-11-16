package com.dulo.service.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.dulo.core.access.port.out.RoleRepository;
import com.dulo.core.access.port.out.UserRepository;
import com.dulo.core.identity.port.in.ExternalSignIn;
import com.dulo.core.identity.port.in.SignInUseCase;
import com.dulo.core.identity.port.in.SigningKeyUseCase;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.tree.port.out.TreeSettingsRepository;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.Permissions;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.identity.SigningKey;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.ExternalIdentity;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;
import com.dulo.service.DuloApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Runs the full server against a real database: Liquibase migrations, the JPA adapter and the REST
 * API. Subclasses choose the database. All tests share one database, so each test uses its own
 * data.
 */
@SpringBootTest(
        classes = DuloApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
            "dulo.features.map-view=false",
            "DULO_CONFIG_FILE=/nonexistent/dulo.yaml",
            "dulo.identity.providers.mock.type=oidc",
            "dulo.identity.providers.mock.client-id=dulo",
            "dulo.identity.providers.mock.client-secret=mock-secret",
            "dulo.identity.providers.mock.role-mappings[family-admins]=admin",
            "dulo.clients.web.redirect-uris[0]=" + OAuthTestClient.REDIRECT_URI,
            "dulo.clients.web.sign-in-url=http://127.0.0.1/sign-in"
        })
abstract class AbstractApiIT {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The identity provider users sign in with. Shared by all databases, never stopped. */
    private static final GenericContainer<?> IDP =
            new GenericContainer<>("ghcr.io/navikt/mock-oauth2-server:6.1.5")
                    .withEnv("JSON_CONFIG", "{\"interactiveLogin\": true}")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/default/.well-known/openid-configuration"));

    static {
        IDP.start();
    }

    @LocalServerPort int port;

    @Autowired TreeSettingsRepository trees;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired SignInUseCase signIn;
    @Autowired SigningKeyUseCase signingKeys;
    @Autowired UnitOfWork unitOfWork;

    /**
     * Points the server at a database container. Call it from each subclass's
     * {@code @DynamicPropertySource} method.
     *
     * @param registry the test's dynamic properties
     * @param db the database container
     */
    /**
     * The server runs on a fixed, free port, because its public URL, which ends up in callback URLs
     * and tokens, must be known before it starts.
     */
    @DynamicPropertySource
    static void server(final DynamicPropertyRegistry registry) throws IOException {
        final int serverPort;
        try (final ServerSocket socket = new ServerSocket(0)) {
            serverPort = socket.getLocalPort();
        }
        registry.add("server.port", () -> serverPort);
        registry.add("dulo.public-url", () -> "http://localhost:" + serverPort);
        registry.add(
                "dulo.identity.providers.mock.issuer-uri",
                () -> "http://localhost:" + IDP.getMappedPort(8080) + "/default");
    }

    static void registerDatabase(
            final DynamicPropertyRegistry registry, final JdbcDatabaseContainer<?> db) {
        registry.add("dulo.database.url", db::getJdbcUrl);
        registry.add("dulo.database.username", db::getUsername);
        registry.add("dulo.database.password", db::getPassword);
    }

    /** The decoded header of a JWT. */
    private static JsonNode header(final String jwt) {
        return JSON.readTree(Base64.getUrlDecoder().decode(jwt.substring(0, jwt.indexOf('.'))));
    }

    private static JsonNode feature(final JsonNode tree, final String key) {
        for (final JsonNode feature : tree.path("features")) {
            if (feature.path("key").asString().equals(key)) {
                return feature;
            }
        }
        throw new AssertionError("Feature " + key + " missing in " + tree);
    }

    @Test
    void apiNeedsAToken() throws Exception {
        final HttpResponse<String> response = this.get("/api/v1/tree");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("Bearer"));
        assertThat(JSON.readTree(response.body()).path("type").asString())
                .isEqualTo("urn:dulo:problem:unauthorized");
        assertThat(this.get("/api/v1/tree", "not-a-token").statusCode()).isEqualTo(401);
        // Paths the server does not serve are closed too.
        assertThat(this.get("/nothing-here").statusCode()).isIn(401, 403);
    }

    @Test
    void signInIssuesTokensForTheDuloUser() throws Exception {
        final OAuthTestClient.Tokens tokens =
                this.client()
                        .signIn(
                                "it-grace",
                                "{\"email\": \"grace@example.com\", \"groups\": [\"family-admins\"]}");
        assertThat(tokens.expiresIn()).isBetween(14 * 60L, 15 * 60L);
        assertThat(tokens.refreshToken()).isNotBlank();

        final UserId id =
                unitOfWork
                        .read(() ->
                                users.findByExternalIdentity(
                                        new ExternalIdentity("mock", "it-grace")))
                        .orElseThrow()
                        .id();
        final HttpResponse<String> me = this.get("/api/v1/me", tokens.accessToken());
        assertThat(me.statusCode()).isEqualTo(200);
        final JsonNode body = JSON.readTree(me.body());
        assertThat(body.path("id").asString()).isEqualTo(id.toString());
        assertThat(body.path("email").asString()).isEqualTo("grace@example.com");
        assertThat(body.path("status").asString()).isEqualTo("active");
        assertThat(body.path("roles").get(0).asString()).isEqualTo("admin");
    }

    @Test
    void idTokensAreNoAccessTokens() throws Exception {
        final OAuthTestClient.Tokens tokens = this.client().signIn("it-id-token", "{}");

        assertThat(header(tokens.accessToken()).path("typ").asString()).isEqualTo("at+jwt");
        assertThat(tokens.idToken()).isNotBlank();
        // Same user, same issuer and signing key, but not an access token.
        final HttpResponse<String> me = this.get("/api/v1/me", tokens.idToken());
        assertThat(me.statusCode()).isEqualTo(401);
        assertThat(this.get("/api/v1/me", tokens.accessToken()).statusCode()).isEqualTo(200);
        // The OpenID Connect user info endpoint takes the same access tokens.
        assertThat(this.get("/userinfo", tokens.accessToken()).statusCode()).isEqualTo(200);
        assertThat(this.get("/userinfo", tokens.idToken()).statusCode()).isEqualTo(401);
    }

    @Test
    void pendingUsersAreToldSo() throws Exception {
        final String token = this.client().signIn("it-pending", "{}").accessToken();

        assertThat(JSON.readTree(this.get("/api/v1/me", token).body()).path("status").asString())
                .isEqualTo("pending");
        final HttpResponse<String> tree = this.get("/api/v1/tree", token);
        assertThat(tree.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(tree.body()).path("type").asString())
                .isEqualTo("urn:dulo:problem:account-pending");
    }

    @Test
    void refreshTokensRotate() throws Exception {
        final OAuthTestClient client = this.client();
        final OAuthTestClient.Tokens first = client.signIn("it-refresh", "{}");

        final OAuthTestClient.Tokens second = client.refresh(first.refreshToken());

        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(this.get("/api/v1/me", second.accessToken()).statusCode()).isEqualTo(200);
        // Postman sends the client id in a Basic header with no password.
        final OAuthTestClient.Tokens third = client.refreshWithBasicHeader(second.refreshToken());
        assertThat(this.get("/api/v1/me", third.accessToken()).statusCode()).isEqualTo(200);
        // A refresh token works once.
        final HttpResponse<String> reused =
                client.tokenRequest(
                        Map.of(
                                "grant_type",
                                "refresh_token",
                                "refresh_token",
                                first.refreshToken(),
                                "client_id",
                                OAuthTestClient.CLIENT_ID));
        assertThat(reused.statusCode()).isEqualTo(400);
        assertThat(reused.body()).contains("invalid_grant");
    }

    @Test
    void unknownProviderHintsGoToTheSignInPage() throws Exception {
        final OAuthTestClient client = this.client();

        final HttpResponse<String> hinted =
                client.get(client.authorizationUrl("x".repeat(43), "ghost"));

        assertThat(hinted.statusCode()).isEqualTo(302);
        assertThat(hinted.headers().firstValue("Location")).hasValue("http://127.0.0.1/sign-in");
    }

    @Test
    void identityProvidersArePublic() throws Exception {
        final HttpResponse<String> response = this.get("/api/v1/identity-providers");

        assertThat(response.statusCode()).isEqualTo(200);
        final JsonNode provider = JSON.readTree(response.body()).get(0);
        assertThat(provider.path("id").asString()).isEqualTo("mock");
        assertThat(provider.path("type").asString()).isEqualTo("oidc");
        assertThat(provider.path("signInPath").asString()).isEqualTo("/oauth2/authorization/mock");
    }

    @Test
    void publishesTheStoredSigningKey() throws Exception {
        final SigningKey key =
                signingKeys.currentOrCreate(() -> {
                    throw new AssertionError("The server creates the key on start");
                });

        final HttpResponse<String> jwks = this.get("/oauth2/jwks");

        assertThat(jwks.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(jwks.body()).path("keys").get(0).path("kid").asString())
                .isEqualTo(key.id());
        assertThat(jwks.body()).doesNotContain("\"d\"");
    }

    @Test
    void treeLifecycle() throws Exception {
        final String token =
                this.client().signIn("it-tree", "{\"groups\": [\"family-admins\"]}").accessToken();

        // Before setup there is no tree.
        final HttpResponse<String> missing = this.get("/api/v1/tree", token);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).contains("application/problem+json"));
        assertThat(JSON.readTree(missing.body()).path("type").asString())
                .isEqualTo("urn:dulo:problem:tree-not-initialized");

        // Store a tree through the outbound port, the way the setup flow will.
        final TreeSettings created =
                TreeSettings.create(
                        "The Smiths", "Since 1820", Set.of(Feature.PEOPLE, Feature.MAP_VIEW));
        final TreeSettings stored = unitOfWork.write(() -> trees.save(created));
        assertThat(stored.version()).isZero();

        final HttpResponse<String> found = this.get("/api/v1/tree", token);
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.headers().firstValue("ETag")).contains("\"0\"");
        final JsonNode body = JSON.readTree(found.body());
        assertThat(body.path("id").asString()).isEqualTo(created.id().toString());
        assertThat(body.path("name").asString()).isEqualTo("The Smiths");
        assertThat(feature(body, "people").path("enabled").asBoolean()).isTrue();
        assertThat(feature(body, "people").path("locked").asBoolean()).isFalse();
        // Enabled in the database, but forced off by configuration.
        assertThat(feature(body, "map-view").path("enabled").asBoolean()).isFalse();
        assertThat(feature(body, "map-view").path("locked").asBoolean()).isTrue();

        // Updates bump the version; stale updates are rejected.
        final TreeSettings renamed =
                new TreeSettings(
                        stored.id(),
                        "Smith family",
                        null,
                        stored.enabledFeatures(),
                        stored.version());
        assertThat(unitOfWork.write(() -> trees.save(renamed)).version()).isEqualTo(1);
        assertThatThrownBy(() -> unitOfWork.write(() -> trees.save(renamed)))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThat(unitOfWork.read(trees::find))
                .hasValueSatisfying(tree -> assertThat(tree.name()).isEqualTo("Smith family"));
    }

    @Test
    void rolesRoundTrip() {
        final RoleKey key = new RoleKey("it-readers");
        final Permissions viewPeople = new Permissions(Map.of(Feature.PEOPLE, Set.of(Access.VIEW)));
        final Role stored =
                unitOfWork.write(() -> roles.save(new Role(key, "Readers", false, viewPeople, 0)));
        assertThat(stored.version()).isZero();
        assertThat(unitOfWork.read(() -> roles.find(key))).contains(stored);

        final Permissions viewAndEdit =
                viewPeople.union(new Permissions(Map.of(Feature.PLACES, Set.of(Access.EDIT))));
        final Role changed = new Role(key, "Editors", false, viewAndEdit, stored.version());
        final Role updated = unitOfWork.write(() -> roles.save(changed));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(unitOfWork.read(() -> roles.find(key))).contains(updated);
        assertThatThrownBy(() -> unitOfWork.write(() -> roles.save(changed)))
                .isInstanceOf(ConcurrentModificationException.class);

        assertThat(unitOfWork.read(() -> roles.findAllByKeys(Set.of(key, new RoleKey("ghost")))))
                .containsExactly(updated);
        assertThat(unitOfWork.read(roles::findAll)).contains(updated);
    }

    @Test
    void usersRoundTrip() {
        final ExternalIdentity identity = new ExternalIdentity("mock", "it-ada");
        final UserId id =
                signIn.signIn(
                        new ExternalSignIn(
                                identity, "ada@example.com", "Ada", Set.of("family-admins")));

        final User user = unitOfWork.read(() -> users.findById(id)).orElseThrow();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.claimRoles()).containsExactly(new RoleKey("admin"));
        assertThat(user.email()).isEqualTo("ada@example.com");
        assertThat(user.lastSignIn()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        assertThat(user.version()).isZero();

        // Manual roles and the status survive the next sign-in; claim roles are recomputed.
        final User changed =
                user.withManualRoles(Set.of(new RoleKey("editor"))).withStatus(UserStatus.DISABLED);
        assertThat(unitOfWork.write(() -> users.save(changed)).version()).isEqualTo(1);
        assertThat(signIn.signIn(new ExternalSignIn(identity, null, "Ada L.", Set.of())))
                .isEqualTo(id);

        final User reloaded =
                unitOfWork.read(() -> users.findByExternalIdentity(identity)).orElseThrow();
        assertThat(reloaded.id()).isEqualTo(id);
        assertThat(reloaded.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(reloaded.claimRoles()).isEmpty();
        assertThat(reloaded.manualRoles()).containsExactly(new RoleKey("editor"));
        assertThat(reloaded.email()).isNull();
        assertThat(reloaded.displayName()).isEqualTo("Ada L.");
        assertThatThrownBy(() -> unitOfWork.write(() -> users.save(changed)))
                .isInstanceOf(ConcurrentModificationException.class);
    }

    @Test
    void errorsOutsideTheApiAreProblems() throws Exception {
        final HttpResponse<String> denied = this.getAsBrowser("/nothing-here");

        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.headers().firstValue("Content-Type"))
                .hasValue("application/problem+json");
        final JsonNode problem = JSON.readTree(denied.body());
        assertThat(problem.path("type").asString()).isEqualTo("urn:dulo:problem:forbidden");
        assertThat(problem.path("instance").asString()).isEqualTo("/nothing-here");

        final HttpResponse<String> invalid =
                this.getAsBrowser("/oauth2/authorize?response_type=code&client_id=ghost");

        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(invalid.body()).path("detail").asString()).contains("client_id");
        assertThat(this.get("/error").statusCode()).isEqualTo(404);
    }

    @Test
    void healthIsUp() throws Exception {
        final HttpResponse<String> health = this.get("/actuator/health/readiness");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(health.body()).path("status").asString()).isEqualTo("UP");
    }

    private OAuthTestClient client() {
        return new OAuthTestClient("http://localhost:" + port);
    }

    private HttpResponse<String> get(final String path) throws IOException, InterruptedException {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(final String path, final String accessToken)
            throws IOException, InterruptedException {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Authorization", "Bearer " + accessToken)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getAsBrowser(final String path)
            throws IOException, InterruptedException {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Accept", "text/html")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
