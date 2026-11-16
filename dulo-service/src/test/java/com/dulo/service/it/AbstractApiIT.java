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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.util.Optional;
import java.util.Set;

/**
 * Runs the full server against a real database: Liquibase migrations, the JPA adapter and the REST
 * API. Subclasses choose the database. All tests share one database, so each test uses its own
 * data. The setup lifecycle runs first; other tests that need a set-up server complete the setup
 * themselves when they run alone.
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
            "dulo.clients.web.sign-in-url=http://127.0.0.1/sign-in",
            "dulo.setup.token=" + AbstractApiIT.SETUP_TOKEN
        })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractApiIT {

    static final String SETUP_TOKEN = "it-setup-token";

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

    private static String problemType(final HttpResponse<String> response) {
        return JSON.readTree(response.body()).path("type").asString();
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
        this.completeSetupIfPending();
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
    @Order(1)
    void setupLifecycle() throws Exception {
        final String token = this.client().signIn("it-root", "{}").accessToken();

        // Until the setup is done, the API is closed, but the setup and /me work.
        assertThat(
                        JSON.readTree(this.get("/api/v1/setup/status").body())
                                .path("pending")
                                .asBoolean())
                .isTrue();
        final HttpResponse<String> closed = this.get("/api/v1/tree", token);
        assertThat(closed.statusCode()).isEqualTo(503);
        assertThat(problemType(closed)).isEqualTo("urn:dulo:problem:setup-required");
        assertThat(JSON.readTree(this.get("/api/v1/me", token).body()).path("status").asString())
                .isEqualTo("pending");

        // The defaults and the configuration need the setup token.
        final HttpResponse<String> noToken = this.get("/api/v1/setup/defaults");
        assertThat(noToken.statusCode()).isEqualTo(403);
        assertThat(problemType(noToken)).isEqualTo("urn:dulo:problem:invalid-setup-token");
        final JsonNode defaults =
                JSON.readTree(
                        this.send("GET", "/api/v1/setup/defaults", null, SETUP_TOKEN, null).body());
        assertThat(defaults.path("roles")).hasSize(4);
        assertThat(defaults.path("registrationMode").asString()).isEqualTo("approval");
        final JsonNode mock = defaults.path("identityProviders").get(0);
        assertThat(mock.path("id").asString()).isEqualTo("mock");
        assertThat(mock.path("roleMappings").path("family-admins").get(0).asString())
                .isEqualTo("admin");
        assertThat(feature(defaults, "map-view").path("locked").asBoolean()).isTrue();
        final String configuration =
                JSON.readTree(
                                this.send("GET", "/api/v1/setup/config", null, SETUP_TOKEN, null)
                                        .body())
                        .path("configuration")
                        .asString();
        assertThat(configuration).contains("client-secret: ****").doesNotContain("mock-secret");

        // Completing it needs a signed-in user, the setup token and a valid request.
        final String request =
                """
                {"name": "The Smiths", "description": "Since 1820",
                 "enabledFeatures": ["people", "map-view"],
                 "roles": [{"key": "it-family", "name": "Family",
                            "permissions": [{"feature": "people", "access": ["view", "edit"]}]}]}
                """;
        assertThat(this.send("POST", "/api/v1/setup", null, SETUP_TOKEN, request).statusCode())
                .isEqualTo(401);
        final HttpResponse<String> wrongToken =
                this.send("POST", "/api/v1/setup", token, "wrong", request);
        assertThat(wrongToken.statusCode()).isEqualTo(403);
        assertThat(problemType(wrongToken)).isEqualTo("urn:dulo:problem:invalid-setup-token");
        final HttpResponse<String> invalid =
                this.send(
                        "POST",
                        "/api/v1/setup",
                        token,
                        SETUP_TOKEN,
                        "{\"name\": \"x\", \"enabledFeatures\": [\"genealogy\"]}");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(problemType(invalid)).isEqualTo("urn:dulo:problem:validation");
        assertThat(JSON.readTree(invalid.body()).path("detail").asString()).contains("genealogy");

        final HttpResponse<String> created =
                this.send("POST", "/api/v1/setup", token, SETUP_TOKEN, request);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).hasValue("/api/v1/tree");
        assertThat(created.headers().firstValue("ETag")).hasValue("\"0\"");
        assertThat(JSON.readTree(created.body()).path("name").asString()).isEqualTo("The Smiths");

        // It works once.
        final HttpResponse<String> again =
                this.send("POST", "/api/v1/setup", token, SETUP_TOKEN, request);
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(problemType(again)).isEqualTo("urn:dulo:problem:setup-completed");
        assertThat(this.send("GET", "/api/v1/setup/defaults", null, SETUP_TOKEN, null).statusCode())
                .isEqualTo(409);
        assertThat(
                        JSON.readTree(this.get("/api/v1/setup/status").body())
                                .path("pending")
                                .asBoolean())
                .isFalse();

        // The caller is root, and the API is open.
        final JsonNode me = JSON.readTree(this.get("/api/v1/me", token).body());
        assertThat(me.path("root").asBoolean()).isTrue();
        assertThat(me.path("status").asString()).isEqualTo("active");
        final HttpResponse<String> found = this.get("/api/v1/tree", token);
        assertThat(found.statusCode()).isEqualTo(200);
        final JsonNode tree = JSON.readTree(found.body());
        assertThat(tree.path("name").asString()).isEqualTo("The Smiths");
        assertThat(tree.path("description").asString()).isEqualTo("Since 1820");
        assertThat(feature(tree, "people").path("enabled").asBoolean()).isTrue();
        assertThat(feature(tree, "timeline").path("enabled").asBoolean()).isFalse();
        // Enabled in the request, but forced off by configuration.
        assertThat(feature(tree, "map-view").path("enabled").asBoolean()).isFalse();
        assertThat(feature(tree, "map-view").path("locked").asBoolean()).isTrue();
        assertThat(unitOfWork.read(() -> roles.find(new RoleKey("it-family"))))
                .hasValueSatisfying(role ->
                        assertThat(role.permissions().allows(Feature.PEOPLE, Access.EDIT))
                                .isTrue());
        assertThat(unitOfWork.read(() -> roles.find(new RoleKey("viewer")))).isPresent();
    }

    @Test
    void administratorsApproveUsersAndAssignRoles() throws Exception {
        this.completeSetupIfPending();
        final String admin =
                this.client().signIn("it-admin", "{\"groups\": [\"family-admins\"]}").accessToken();
        final String newcomer = this.client().signIn("it-newcomer", "{}").accessToken();
        final String newcomerId =
                JSON.readTree(this.get("/api/v1/me", newcomer).body()).path("id").asString();
        final String path = "/api/v1/admin/users/" + newcomerId;

        // A pending user can't read the tree or administer anyone.
        assertThat(problemType(this.get("/api/v1/tree", newcomer)))
                .isEqualTo("urn:dulo:problem:account-pending");
        assertThat(this.get("/api/v1/admin/users", newcomer).statusCode()).isEqualTo(403);

        final JsonNode users = JSON.readTree(this.get("/api/v1/admin/users", admin).body());
        assertThat(users.findValuesAsString("id")).contains(newcomerId);
        final HttpResponse<String> read = this.get(path, admin);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(read.body()).path("status").asString()).isEqualTo("pending");
        final String etag = read.headers().firstValue("ETag").orElseThrow();

        final String approve = "{\"status\": \"active\", \"manualRoles\": [\"viewer\"]}";
        final HttpResponse<String> noIfMatch = this.call("PATCH", path, admin, approve);
        assertThat(noIfMatch.statusCode()).isEqualTo(428);
        assertThat(problemType(noIfMatch)).isEqualTo("urn:dulo:problem:precondition-required");
        final HttpResponse<String> stale =
                this.call("PATCH", path, admin, approve, "If-Match", "\"99\"");
        assertThat(stale.statusCode()).isEqualTo(412);
        assertThat(problemType(stale)).isEqualTo("urn:dulo:problem:concurrent-modification");
        final HttpResponse<String> unknownRole =
                this.call(
                        "PATCH", path, admin, "{\"manualRoles\": [\"ghosts\"]}", "If-Match", etag);
        assertThat(unknownRole.statusCode()).isEqualTo(400);
        assertThat(problemType(unknownRole)).isEqualTo("urn:dulo:problem:validation");

        final HttpResponse<String> approved =
                this.call("PATCH", path, admin, approve, "If-Match", etag);
        assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
        assertThat(approved.headers().firstValue("ETag"))
                .isPresent()
                .isNotEqualTo(Optional.of(etag));
        final JsonNode user = JSON.readTree(approved.body());
        assertThat(user.path("status").asString()).isEqualTo("active");
        assertThat(user.path("manualRoles").get(0).asString()).isEqualTo("viewer");

        // Now a viewer: the tree is open, the administration isn't.
        assertThat(this.get("/api/v1/tree", newcomer).statusCode()).isEqualTo(200);
        final HttpResponse<String> forbidden = this.get("/api/v1/admin/users", newcomer);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(problemType(forbidden)).isEqualTo("urn:dulo:problem:forbidden");
        assertThat(this.get("/api/v1/admin/roles", newcomer).statusCode()).isEqualTo(403);

        // Administrators can't lock themselves out.
        final String adminId =
                JSON.readTree(this.get("/api/v1/me", admin).body()).path("id").asString();
        final HttpResponse<String> self = this.get("/api/v1/admin/users/" + adminId, admin);
        assertThat(
                        this.call(
                                        "PATCH",
                                        "/api/v1/admin/users/" + adminId,
                                        admin,
                                        "{\"status\": \"disabled\"}",
                                        "If-Match",
                                        self.headers().firstValue("ETag").orElseThrow())
                                .statusCode())
                .isEqualTo(400);
        assertThat(this.get("/api/v1/admin/users/" + UserId.newId(), admin).statusCode())
                .isEqualTo(404);
    }

    @Test
    void administratorsManageRoles() throws Exception {
        this.completeSetupIfPending();
        final String admin =
                this.client()
                        .signIn("it-role-admin", "{\"groups\": [\"family-admins\"]}")
                        .accessToken();
        final String root = this.client().signIn("it-root", "{}").accessToken();
        final String role =
                """
                {"key": "it-helpers", "name": "Helpers",
                 "permissions": [{"feature": "places", "access": ["view", "create"]}]}
                """;

        final HttpResponse<String> created = this.call("POST", "/api/v1/admin/roles", admin, role);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location"))
                .hasValue("/api/v1/admin/roles/it-helpers");
        final HttpResponse<String> duplicate =
                this.call("POST", "/api/v1/admin/roles", admin, role);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(problemType(duplicate)).isEqualTo("urn:dulo:problem:role-exists");
        final HttpResponse<String> renamed =
                this.call(
                        "PATCH",
                        "/api/v1/admin/roles/it-helpers",
                        admin,
                        "{\"name\": \"Helpers of the tree\"}",
                        "If-Match",
                        created.headers().firstValue("ETag").orElseThrow());
        assertThat(renamed.statusCode()).isEqualTo(200);
        final JsonNode helpers = JSON.readTree(renamed.body());
        assertThat(helpers.path("name").asString()).isEqualTo("Helpers of the tree");
        assertThat(helpers.path("permissions").get(0).path("access")).hasSize(2);
        assertThat(
                        JSON.readTree(this.get("/api/v1/admin/roles", admin).body())
                                .findValuesAsString("key"))
                .contains("admin", "viewer", "it-helpers");

        // Built-in roles: only root changes them, nobody deletes them.
        final String viewerETag =
                this.get("/api/v1/admin/roles/viewer", admin)
                        .headers()
                        .firstValue("ETag")
                        .orElseThrow();
        final String sameName = "{\"name\": \"Viewer\"}";
        assertThat(
                        this.call(
                                        "PATCH",
                                        "/api/v1/admin/roles/viewer",
                                        admin,
                                        sameName,
                                        "If-Match",
                                        viewerETag)
                                .statusCode())
                .isEqualTo(403);
        final HttpResponse<String> byRoot =
                this.call(
                        "PATCH",
                        "/api/v1/admin/roles/viewer",
                        root,
                        sameName,
                        "If-Match",
                        viewerETag);
        assertThat(byRoot.statusCode()).as(byRoot.body()).isEqualTo(200);
        assertThat(
                        this.call(
                                        "DELETE",
                                        "/api/v1/admin/roles/viewer",
                                        root,
                                        null,
                                        "If-Match",
                                        byRoot.headers().firstValue("ETag").orElseThrow())
                                .statusCode())
                .isEqualTo(400);

        // A role in use stays; an unused one goes.
        final String helperId =
                JSON.readTree(
                                this.get(
                                                "/api/v1/me",
                                                this.client()
                                                        .signIn("it-helper", "{}")
                                                        .accessToken())
                                        .body())
                        .path("id")
                        .asString();
        final String helperPath = "/api/v1/admin/users/" + helperId;
        assertThat(
                        this.call(
                                        "PATCH",
                                        helperPath,
                                        admin,
                                        "{\"manualRoles\": [\"it-helpers\"]}",
                                        "If-Match",
                                        this.get(helperPath, admin)
                                                .headers()
                                                .firstValue("ETag")
                                                .orElseThrow())
                                .statusCode())
                .isEqualTo(200);
        final String helpersETag = renamed.headers().firstValue("ETag").orElseThrow();
        final HttpResponse<String> inUse =
                this.call(
                        "DELETE",
                        "/api/v1/admin/roles/it-helpers",
                        admin,
                        null,
                        "If-Match",
                        helpersETag);
        assertThat(inUse.statusCode()).isEqualTo(409);
        assertThat(problemType(inUse)).isEqualTo("urn:dulo:problem:role-in-use");
        final JsonNode usage = JSON.readTree(inUse.body());
        assertThat(usage.path("users")).hasSize(1);
        assertThat(usage.path("users").get(0).asString()).isEqualTo(helperId);
        assertThat(usage.path("identityProviders")).isEmpty();
        assertThat(usage.path("detail").asString()).contains("1 user has it");
        assertThat(
                        this.call(
                                        "PATCH",
                                        helperPath,
                                        admin,
                                        "{\"manualRoles\": []}",
                                        "If-Match",
                                        this.get(helperPath, admin)
                                                .headers()
                                                .firstValue("ETag")
                                                .orElseThrow())
                                .statusCode())
                .isEqualTo(200);
        assertThat(
                        this.call(
                                        "DELETE",
                                        "/api/v1/admin/roles/it-helpers",
                                        admin,
                                        null,
                                        "If-Match",
                                        helpersETag)
                                .statusCode())
                .isEqualTo(204);
        final HttpResponse<String> gone = this.get("/api/v1/admin/roles/it-helpers", admin);
        assertThat(gone.statusCode()).isEqualTo(404);
        assertThat(problemType(gone)).isEqualTo("urn:dulo:problem:not-found");
    }

    @Test
    void partialAdministratorsCantGrantMoreThanTheyHave() throws Exception {
        this.completeSetupIfPending();
        final String root = this.client().signIn("it-root", "{}").accessToken();
        final String moderator = this.client().signIn("it-moderator", "{}").accessToken();
        final String moderatorPath =
                "/api/v1/admin/users/"
                        + JSON.readTree(this.get("/api/v1/me", moderator).body())
                                .path("id")
                                .asString();

        // Root makes a role that may approve users and see people, and gives it to the moderator.
        final HttpResponse<String> created =
                this.call(
                        "POST",
                        "/api/v1/admin/roles",
                        root,
                        """
                        {"key": "it-moderators", "name": "Moderators",
                         "permissions": [{"feature": "administration", "access": ["view", "edit"]},
                                         {"feature": "people", "access": ["view"]}]}
                        """);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        final HttpResponse<String> assigned =
                this.call(
                        "PATCH",
                        moderatorPath,
                        root,
                        "{\"status\": \"active\", \"manualRoles\": [\"it-moderators\"]}",
                        "If-Match",
                        this.get(moderatorPath, root).headers().firstValue("ETag").orElseThrow());
        assertThat(assigned.statusCode()).as(assigned.body()).isEqualTo(200);

        // The moderator can't make themselves an administrator, nor widen their role.
        final HttpResponse<String> selfPromotion =
                this.call(
                        "PATCH",
                        moderatorPath,
                        moderator,
                        "{\"manualRoles\": [\"it-moderators\", \"admin\"]}",
                        "If-Match",
                        assigned.headers().firstValue("ETag").orElseThrow());
        assertThat(selfPromotion.statusCode()).isEqualTo(403);
        assertThat(problemType(selfPromotion)).isEqualTo("urn:dulo:problem:forbidden");
        assertThat(JSON.readTree(selfPromotion.body()).path("detail").asString())
                .contains("'admin'");
        final HttpResponse<String> widened =
                this.call(
                        "PATCH",
                        "/api/v1/admin/roles/it-moderators",
                        moderator,
                        "{\"permissions\": [{\"feature\": \"administration\","
                                + " \"access\": [\"view\", \"create\", \"edit\", \"delete\"]}]}",
                        "If-Match",
                        created.headers().firstValue("ETag").orElseThrow());
        assertThat(widened.statusCode()).isEqualTo(403);
    }

    @Test
    void administratorsChangeTheTree() throws Exception {
        this.completeSetupIfPending();
        final String admin =
                this.client()
                        .signIn("it-tree-admin", "{\"groups\": [\"family-admins\"]}")
                        .accessToken();
        final String viewer = this.client().signIn("it-tree-viewer", "{}").accessToken();
        final String etag =
                this.get("/api/v1/tree", admin).headers().firstValue("ETag").orElseThrow();
        final String change =
                "{\"description\": \"Changed in a test\", \"enabledFeatures\": [\"people\", \"timeline\"]}";

        assertThat(
                        this.call("PATCH", "/api/v1/tree", viewer, change, "If-Match", etag)
                                .statusCode())
                .isEqualTo(403);
        final HttpResponse<String> changed =
                this.call("PATCH", "/api/v1/tree", admin, change, "If-Match", etag);
        assertThat(changed.statusCode()).as(changed.body()).isEqualTo(200);
        final JsonNode tree = JSON.readTree(changed.body());
        assertThat(tree.path("description").asString()).isEqualTo("Changed in a test");
        assertThat(feature(tree, "timeline").path("enabled").asBoolean()).isTrue();
        assertThat(feature(tree, "families").path("enabled").asBoolean()).isFalse();
        assertThat(changed.headers().firstValue("ETag"))
                .isPresent()
                .isNotEqualTo(Optional.of(etag));
        assertThat(this.call("PATCH", "/api/v1/tree", admin, change, "If-Match", etag).statusCode())
                .isEqualTo(412);
    }

    @Test
    void treeSettingsRoundTrip() throws Exception {
        this.completeSetupIfPending();
        final TreeSettings stored = unitOfWork.read(trees::find).orElseThrow();

        // Updates bump the version; stale updates are rejected.
        final TreeSettings renamed =
                new TreeSettings(
                        stored.id(),
                        stored.name(),
                        "Renamed in a test",
                        stored.enabledFeatures(),
                        stored.version());
        assertThat(unitOfWork.write(() -> trees.save(renamed)).version())
                .isEqualTo(stored.version() + 1);
        assertThatThrownBy(() -> unitOfWork.write(() -> trees.save(renamed)))
                .isInstanceOf(ConcurrentModificationException.class);
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

    /** Completes the setup as "it-root" unless an earlier test did. */
    private void completeSetupIfPending() throws Exception {
        if (!JSON.readTree(this.get("/api/v1/setup/status").body()).path("pending").asBoolean()) {
            return;
        }
        final String token = this.client().signIn("it-root", "{}").accessToken();
        final HttpResponse<String> created =
                this.send(
                        "POST",
                        "/api/v1/setup",
                        token,
                        SETUP_TOKEN,
                        "{\"name\": \"The Smiths\", \"enabledFeatures\": [\"people\"]}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    }

    /** A request with an access token, an optional JSON body and header name-value pairs. */
    private HttpResponse<String> call(
            final String method,
            final String path,
            final String accessToken,
            final @Nullable String json,
            final String... headers)
            throws IOException, InterruptedException {
        final HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .method(
                                method,
                                json == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(json))
                        .header("Authorization", "Bearer " + accessToken);
        if (json != null) {
            request.header("Content-Type", "application/json");
        }
        if (headers.length > 0) {
            request.headers(headers);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** A request with optional access token, setup token and JSON body. */
    private HttpResponse<String> send(
            final String method,
            final String path,
            final @Nullable String accessToken,
            final @Nullable String setupToken,
            final @Nullable String json)
            throws IOException, InterruptedException {
        final HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .method(
                                method,
                                json == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            request.header("Content-Type", "application/json");
        }
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        if (setupToken != null) {
            request.header("X-Dulo-Setup-Token", setupToken);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
