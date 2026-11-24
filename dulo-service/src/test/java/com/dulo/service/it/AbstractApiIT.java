package com.dulo.service.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.dulo.core.access.port.out.RoleRepository;
import com.dulo.core.access.port.out.UserRepository;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.identity.port.in.ExternalSignIn;
import com.dulo.core.identity.port.in.SignInUseCase;
import com.dulo.core.identity.port.in.SigningKeyUseCase;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.tree.port.out.TreeSettingsRepository;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.Permissions;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.ChangeEventQuery;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.audit.Link;
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
import org.springframework.boot.info.BuildProperties;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    static final String BACK_CHANNEL_USER = "it-back-channel";

    /**
     * The identity provider users sign in with. Shared by all databases, never stopped. A client
     * credentials request with {@code scope=back-channel-logout} gets a logout token for {@link
     * #BACK_CHANNEL_USER}, signed with the provider's key.
     */
    private static final GenericContainer<?> IDP =
            new GenericContainer<>("ghcr.io/navikt/mock-oauth2-server:6.1.5")
                    .withEnv(
                            "JSON_CONFIG",
                            """
                            {
                              "interactiveLogin": true,
                              "tokenCallbacks": [{
                                "issuerId": "default",
                                "requestMappings": [{
                                  "requestParam": "scope",
                                  "match": "back-channel-logout",
                                  "claims": {
                                    "sub": "%s",
                                    "aud": ["dulo"],
                                    "events": {
                                      "http://schemas.openid.net/event/backchannel-logout": {}
                                    }
                                  }
                                }]
                              }]
                            }
                            """
                                    .formatted(BACK_CHANNEL_USER))
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
    @Autowired ChangeEventRepository changeEvents;
    @Autowired BuildProperties build;

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

    /** The same event with a new id, as a second event of the same record. */
    private static ChangeEvent copyWithNewId(final ChangeEvent event) {
        return new ChangeEvent(
                ChangeEventId.newId(),
                0,
                event.at(),
                event.actor(),
                event.entityType(),
                event.entityId(),
                event.entityVersion(),
                event.kind(),
                event.correlationId(),
                event.changes(),
                event.removedLinks());
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
    void signingOutEndsThatSignInOnly() throws Exception {
        final OAuthTestClient browser = this.client();
        final OAuthTestClient.Tokens signedIn = browser.signIn("it-sign-out", "{}");
        final OAuthTestClient.Tokens refreshed = browser.refresh(signedIn.refreshToken());
        final OAuthTestClient otherDevice = this.client();
        final OAuthTestClient.Tokens other = otherDevice.signIn("it-sign-out", "{}");

        // The web client signs out with the ID token it got last.
        final HttpResponse<String> signOut = browser.signOut(refreshed.idToken());

        assertThat(signOut.statusCode()).isEqualTo(302);
        assertThat(signOut.headers().firstValue("Location")).hasValue(OAuthTestClient.SIGN_IN_URL);
        final HttpResponse<String> afterSignOut =
                browser.tokenRequest(
                        Map.of(
                                "grant_type",
                                "refresh_token",
                                "refresh_token",
                                refreshed.refreshToken(),
                                "client_id",
                                OAuthTestClient.CLIENT_ID));
        assertThat(afterSignOut.statusCode()).isEqualTo(400);
        assertThat(afterSignOut.body()).contains("invalid_grant");
        // The other device's sign-in goes on.
        assertThat(otherDevice.refresh(other.refreshToken()).accessToken()).isNotBlank();
    }

    @Test
    void identityProvidersEndSignInsThroughTheBackChannel() throws Exception {
        final OAuthTestClient browser = this.client();
        final OAuthTestClient.Tokens signedIn = browser.signIn(BACK_CHANNEL_USER, "{}");
        final OAuthTestClient otherUser = this.client();
        final OAuthTestClient.Tokens other = otherUser.signIn("it-back-channel-other", "{}");

        final HttpResponse<String> logout = browser.backChannelLogout("mock", this.logoutToken());

        assertThat(logout.statusCode()).as(logout.body()).isEqualTo(200);
        // The refresh token stops working.
        final HttpResponse<String> refresh =
                browser.tokenRequest(
                        Map.of(
                                "grant_type",
                                "refresh_token",
                                "refresh_token",
                                signedIn.refreshToken(),
                                "client_id",
                                OAuthTestClient.CLIENT_ID));
        assertThat(refresh.statusCode()).isEqualTo(400);
        assertThat(refresh.body()).contains("invalid_grant");
        // The browser session is gone, so a new authorization request goes to the provider again.
        final HttpResponse<String> authorize =
                browser.get(browser.authorizationUrl("x".repeat(43), null));
        assertThat(authorize.statusCode()).isEqualTo(302);
        assertThat(authorize.headers().firstValue("Location").orElseThrow())
                .endsWith("/oauth2/authorization/mock");
        // Other users stay signed in.
        assertThat(otherUser.refresh(other.refreshToken()).accessToken()).isNotBlank();
    }

    @Test
    void unknownProviderHintsGoToTheSignInPage() throws Exception {
        final OAuthTestClient client = this.client();

        final HttpResponse<String> hinted =
                client.get(client.authorizationUrl("x".repeat(43), "ghost"));

        assertThat(hinted.statusCode()).isEqualTo(302);
        assertThat(hinted.headers().firstValue("Location")).hasValue(OAuthTestClient.SIGN_IN_URL);
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
    void publicEndpointsIgnoreInvalidTokens() throws Exception {
        // Clients may still hold an expired token when they show the sign-in page.
        for (final String path :
                List.of(
                        "/api/v1/identity-providers",
                        "/api/v1/openapi.json",
                        "/api/v1/setup/status")) {
            assertThat(this.get(path, "expired-or-invalid").statusCode()).as(path).isEqualTo(200);
        }
        final HttpResponse<String> protectedEndpoint =
                this.get("/api/v1/tree", "expired-or-invalid");
        assertThat(protectedEndpoint.statusCode()).isEqualTo(401);
        assertThat(problemType(protectedEndpoint)).isEqualTo("urn:dulo:problem:unauthorized");
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
        assertThat(mock.path("roleMappings").path("family-admins").asString()).isEqualTo("admin");
        assertThat(feature(defaults, "map-view").path("locked").asBoolean()).isTrue();
        final String configuration =
                JSON.readTree(
                                this.send("GET", "/api/v1/setup/config", null, SETUP_TOKEN, null)
                                        .body())
                        .path("configuration")
                        .asString();
        assertThat(configuration).contains("client-secret: \"****\"").doesNotContain("mock-secret");

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
    void activeUsersSeeTheServerVersion() throws Exception {
        this.completeSetupIfPending();
        final String admin = this.signInAsAdmin("it-version-admin");
        final String newcomer = this.client().signIn("it-version-newcomer", "{}").accessToken();

        final HttpResponse<String> server = this.get("/api/v1/server", admin);
        assertThat(server.statusCode()).as(server.body()).isEqualTo(200);
        assertThat(JSON.readTree(server.body()).path("version").asString())
                .isEqualTo(this.build.getVersion());
        assertThat(problemType(this.get("/api/v1/server", newcomer)))
                .isEqualTo("urn:dulo:problem:account-pending");
        assertThat(this.get("/api/v1/server").statusCode()).isEqualTo(401);
    }

    @Test
    void theAuditLogShowsEveryChange() throws Exception {
        this.completeSetupIfPending();
        final String admin =
                this.client()
                        .signIn("it-audit-admin", "{\"groups\": [\"family-admins\"]}")
                        .accessToken();
        final String adminId =
                JSON.readTree(this.get("/api/v1/me", admin).body()).path("id").asString();
        final String treeETag =
                this.get("/api/v1/tree", admin).headers().firstValue("ETag").orElseThrow();
        final HttpResponse<String> enabled =
                this.call(
                        "PATCH",
                        "/api/v1/tree",
                        admin,
                        "{\"description\": \"Audited "
                                + UUID.randomUUID()
                                + "\", \"enabledFeatures\": [\"people\", \"audit-log\"]}",
                        "If-Match",
                        treeETag);
        assertThat(enabled.statusCode()).as(enabled.body()).isEqualTo(200);

        // A role created, renamed and deleted: three events, newest first.
        final HttpResponse<String> created =
                this.call(
                        "POST",
                        "/api/v1/admin/roles",
                        admin,
                        "{\"key\": \"it-audited\", \"name\": \"Audited\", \"permissions\": []}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        final HttpResponse<String> renamed =
                this.call(
                        "PATCH",
                        "/api/v1/admin/roles/it-audited",
                        admin,
                        "{\"name\": \"Audited again\"}",
                        "If-Match",
                        created.headers().firstValue("ETag").orElseThrow());
        assertThat(renamed.statusCode()).as(renamed.body()).isEqualTo(200);
        assertThat(
                        this.call(
                                        "DELETE",
                                        "/api/v1/admin/roles/it-audited",
                                        admin,
                                        null,
                                        "If-Match",
                                        renamed.headers().firstValue("ETag").orElseThrow())
                                .statusCode())
                .isEqualTo(204);
        final String rolePath = "/api/v1/audit?entityType=role&entityId=it-audited";
        final HttpResponse<String> history = this.get(rolePath, admin);
        assertThat(history.statusCode()).as(history.body()).isEqualTo(200);
        final JsonNode events = JSON.readTree(history.body()).path("items");
        assertThat(events.findValuesAsString("kind"))
                .containsExactly("deleted", "changed", "created");
        assertThat(events.findValuesAsString("actor")).containsOnly(adminId);
        final JsonNode rename = events.get(1);
        assertThat(rename.path("entityVersion").asLong()).isEqualTo(1);
        assertThat(rename.path("changes")).hasSize(1);
        assertThat(rename.path("changes").get(0).path("field").asString()).isEqualTo("name");
        assertThat(rename.path("changes").get(0).path("before").asString()).isEqualTo("Audited");
        assertThat(rename.path("changes").get(0).path("after").asString())
                .isEqualTo("Audited again");
        assertThat(events.get(2).path("changes").findValuesAsString("field"))
                .containsExactly("name", "builtIn", "permissions");
        assertThat(events.get(2).path("changes").get(1).path("after").asBoolean()).isFalse();

        // In pages.
        final JsonNode first = JSON.readTree(this.get(rolePath + "&limit=2", admin).body());
        assertThat(first.path("items")).hasSize(2);
        final HttpResponse<String> secondPage =
                this.get(rolePath + "&limit=2&after=" + first.path("next").asString(), admin);
        final JsonNode second = JSON.readTree(secondPage.body());
        assertThat(second.path("items").findValuesAsString("kind")).containsExactly("created");
        assertThat(second.path("next").isNull()).isTrue();

        // The tree change, and the filters by actor and time.
        final JsonNode treeChange =
                JSON.readTree(this.get("/api/v1/audit?entityType=tree&limit=1", admin).body())
                        .path("items")
                        .get(0);
        assertThat(treeChange.path("kind").asString()).isEqualTo("changed");
        assertThat(treeChange.path("actor").asString()).isEqualTo(adminId);
        final JsonNode byAdmin =
                JSON.readTree(
                        this.get(
                                        "/api/v1/audit?actor="
                                                + adminId
                                                + "&from="
                                                + treeChange.path("at").asString(),
                                        admin)
                                .body());
        assertThat(byAdmin.path("items")).hasSize(4);
        // Before that, the admin's first sign-in created their user.
        final JsonNode beforeTreeChange =
                JSON.readTree(
                                this.get(
                                                "/api/v1/audit?actor="
                                                        + adminId
                                                        + "&to="
                                                        + treeChange.path("at").asString(),
                                                admin)
                                        .body())
                        .path("items");
        assertThat(beforeTreeChange).hasSize(1);
        assertThat(beforeTreeChange.get(0).path("entityType").asString()).isEqualTo("user");
        assertThat(beforeTreeChange.get(0).path("kind").asString()).isEqualTo("created");

        // Invalid requests.
        for (final String invalid :
                List.of("?limit=0", "?entityType=ghosts", "?after=not-a-cursor", "?actor=x")) {
            final HttpResponse<String> response = this.get("/api/v1/audit" + invalid, admin);
            assertThat(response.statusCode()).as(invalid).isEqualTo(400);
            assertThat(problemType(response)).isEqualTo("urn:dulo:problem:validation");
        }
    }

    @Test
    void onlyAdministratorsSeeAdministrativeChanges() throws Exception {
        this.completeSetupIfPending();
        final String admin =
                this.client()
                        .signIn("it-audit-root-admin", "{\"groups\": [\"family-admins\"]}")
                        .accessToken();
        final String editor = this.client().signIn("it-audit-editor", "{}").accessToken();
        final String editorPath =
                "/api/v1/admin/users/"
                        + JSON.readTree(this.get("/api/v1/me", editor).body())
                                .path("id")
                                .asString();
        final HttpResponse<String> approved =
                this.call(
                        "PATCH",
                        editorPath,
                        admin,
                        "{\"status\": \"active\", \"manualRoles\": [\"editor\"]}",
                        "If-Match",
                        this.get(editorPath, admin).headers().firstValue("ETag").orElseThrow());
        assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
        final String treeETag =
                this.get("/api/v1/tree", admin).headers().firstValue("ETag").orElseThrow();
        assertThat(
                        this.call(
                                        "PATCH",
                                        "/api/v1/tree",
                                        admin,
                                        "{\"enabledFeatures\": [\"people\", \"audit-log\"]}",
                                        "If-Match",
                                        treeETag)
                                .statusCode())
                .isEqualTo(200);

        // The approval is in the history of the user, for administrators only.
        final JsonNode approval =
                JSON.readTree(
                                this.get(
                                                "/api/v1/audit?entityType=user&limit=1&entityId="
                                                        + editorPath.substring(
                                                                editorPath.lastIndexOf('/') + 1),
                                                admin)
                                        .body())
                        .path("items")
                        .get(0);
        assertThat(approval.path("changes").findValuesAsString("field"))
                .containsExactly("status", "manualRoles");
        final HttpResponse<String> all = this.get("/api/v1/audit", editor);
        assertThat(all.statusCode()).as(all.body()).isEqualTo(200);
        assertThat(JSON.readTree(all.body()).path("items").findValuesAsString("entityType"))
                .doesNotContain("role", "tree", "user");
        final HttpResponse<String> roles = this.get("/api/v1/audit?entityType=role", editor);
        assertThat(roles.statusCode()).isEqualTo(403);
        assertThat(problemType(roles)).isEqualTo("urn:dulo:problem:forbidden");
    }

    @Test
    void placesLifecycle() throws Exception {
        this.completeSetupIfPending();
        final String admin = this.signInAsAdmin("it-places-admin");
        this.enableFeatures(admin, "people", "places", "audit-log");
        final String token = UUID.randomUUID().toString().substring(0, 8);

        // A county with a town in it.
        final HttpResponse<String> county =
                this.call(
                        "POST",
                        "/api/v1/places",
                        admin,
                        "{\"name\": \"Kreis Ä "
                                + token
                                + "\", \"type\": \"county\","
                                + " \"coordinates\": {\"latitude\": 48.5, \"longitude\": 11.25}}");
        assertThat(county.statusCode()).as(county.body()).isEqualTo(201);
        final String countyId = JSON.readTree(county.body()).path("id").asString();
        assertThat(county.headers().firstValue("Location")).hasValue("/api/v1/places/" + countyId);
        final HttpResponse<String> town =
                this.call(
                        "POST",
                        "/api/v1/places",
                        admin,
                        "{\"name\": \"Dorf 100% "
                                + token
                                + "\", \"type\": \"village\", \"enclosedBy\": \""
                                + countyId
                                + "\"}");
        assertThat(town.statusCode()).as(town.body()).isEqualTo(201);
        final String townId = JSON.readTree(town.body()).path("id").asString();
        final JsonNode read = JSON.readTree(this.get("/api/v1/places/" + countyId, admin).body());
        assertThat(read.path("name").asString()).isEqualTo("Kreis Ä " + token);
        assertThat(read.path("coordinates").path("longitude").asDouble()).isEqualTo(11.25);

        // Replacing needs the current version, and a place can't end up inside itself.
        final String townPath = "/api/v1/places/" + townId;
        final String renamed =
                "{\"name\": \"Neudorf 100% "
                        + token
                        + "\", \"type\": \"village\", \"enclosedBy\": \""
                        + countyId
                        + "\"}";
        assertThat(this.call("PUT", townPath, admin, renamed).statusCode()).isEqualTo(428);
        final HttpResponse<String> replaced =
                this.call("PUT", townPath, admin, renamed, "If-Match", "\"0\"");
        assertThat(replaced.statusCode()).as(replaced.body()).isEqualTo(200);
        assertThat(replaced.headers().firstValue("ETag")).hasValue("\"1\"");
        assertThat(this.call("PUT", townPath, admin, renamed, "If-Match", "\"0\"").statusCode())
                .isEqualTo(412);
        final HttpResponse<String> loop =
                this.call(
                        "PUT",
                        "/api/v1/places/" + countyId,
                        admin,
                        "{\"name\": \"Kreis\", \"type\": \"county\", \"enclosedBy\": \""
                                + townId
                                + "\"}",
                        "If-Match",
                        "\"0\"");
        assertThat(loop.statusCode()).isEqualTo(400);
        assertThat(problemType(loop)).isEqualTo("urn:dulo:problem:validation");

        // Search, with like wildcards taken literally, and paging.
        final JsonNode found =
                JSON.readTree(this.get("/api/v1/places?q=" + token + "&limit=1", admin).body());
        assertThat(found.path("items").findValuesAsString("id")).containsExactly(countyId);
        final JsonNode rest =
                JSON.readTree(
                        this.get(
                                        "/api/v1/places?q="
                                                + token
                                                + "&limit=1&after="
                                                + found.path("next").asString(),
                                        admin)
                                .body());
        assertThat(rest.path("items").findValuesAsString("id")).containsExactly(townId);
        assertThat(rest.path("next").isNull()).isTrue();
        assertThat(
                        JSON.readTree(this.get("/api/v1/places?q=0%25%20" + token, admin).body())
                                .path("items")
                                .findValuesAsString("id"))
                .containsExactly(townId);
        assertThat(
                        JSON.readTree(this.get("/api/v1/places?q=_" + token, admin).body())
                                .path("items"))
                .isEmpty();

        // Deleting the county needs confirmation, since the town lies in it.
        final String countyPath = "/api/v1/places/" + countyId;
        final HttpResponse<String> linked =
                this.call("DELETE", countyPath, admin, null, "If-Match", "\"0\"");
        assertThat(linked.statusCode()).isEqualTo(409);
        assertThat(problemType(linked)).isEqualTo("urn:dulo:problem:has-links");
        final JsonNode link = JSON.readTree(linked.body()).path("links").get(0);
        assertThat(link.path("entityType").asString()).isEqualTo("place");
        assertThat(link.path("entityId").asString()).isEqualTo(townId);
        assertThat(link.path("field").asString()).isEqualTo("enclosedBy");
        assertThat(
                        this.call(
                                        "DELETE",
                                        countyPath + "?confirmed=true",
                                        admin,
                                        null,
                                        "If-Match",
                                        "\"0\"")
                                .statusCode())
                .isEqualTo(204);
        assertThat(this.get(countyPath, admin).statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(this.get(townPath, admin).body()).path("enclosedBy").isNull())
                .isTrue();
        final JsonNode history = JSON.readTree(this.get(countyPath + "/history", admin).body());
        assertThat(history.findValuesAsString("kind")).containsExactly("created", "deleted");
        assertThat(history.get(1).path("removedLinks").get(0).path("entityId").asString())
                .isEqualTo(townId);
        assertThat(this.get("/api/v1/places/" + UUID.randomUUID() + "/history", admin).statusCode())
                .isEqualTo(404);
        assertThat(this.get("/api/v1/places/not-a-uuid", admin).statusCode()).isEqualTo(400);

        // A disabled feature is denied to everyone, administrators included.
        this.enableFeatures(admin, "people", "audit-log");
        final HttpResponse<String> disabled = this.get("/api/v1/places", admin);
        assertThat(disabled.statusCode()).isEqualTo(403);
        assertThat(problemType(disabled)).isEqualTo("urn:dulo:problem:feature-disabled");
    }

    @Test
    void peopleLifecycle() throws Exception {
        this.completeSetupIfPending();
        final String admin = this.signInAsAdmin("it-people-admin");
        this.enableFeatures(admin, "people", "audit-log");
        final String token = UUID.randomUUID().toString().substring(0, 8);
        final String anna =
                "{\"names\": [{\"type\": \"married\", \"given\": \"Anna\", \"surname\": \"Zett"
                        + token
                        + "\"}, {\"type\": \"birth\", \"given\": \"Anna\", \"surname\": \"Müller"
                        + token
                        + "\", \"nickname\": \"Annie\"}], \"sex\": \"female\", \"notes\": \"Farmer\"}";

        final HttpResponse<String> created = this.call("POST", "/api/v1/people", admin, anna);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        final JsonNode person = JSON.readTree(created.body());
        final String path = "/api/v1/people/" + person.path("id").asString();
        assertThat(person.path("names")).hasSize(2);
        assertThat(person.path("names").get(1).path("nickname").asString()).isEqualTo("Annie");
        assertThat(person.path("names").get(1).path("prefix").isNull()).isTrue();
        final HttpResponse<String> hans =
                this.call(
                        "POST",
                        "/api/v1/people",
                        admin,
                        "{\"names\": [{\"type\": \"birth\", \"given\": \"Hans\", \"surname\": \"Abel"
                                + token
                                + "\"}], \"sex\": \"male\"}");
        assertThat(hans.statusCode()).as(hans.body()).isEqualTo(201);

        // Sorted by the primary given name; the search looks in every name, ignoring case.
        assertThat(
                        JSON.readTree(this.get("/api/v1/people?q=" + token, admin).body())
                                .path("items")
                                .findValuesAsString("given"))
                .containsExactly("Anna", "Anna", "Hans");
        assertThat(
                        JSON.readTree(
                                        this.get("/api/v1/people?sort=surname&q=" + token, admin)
                                                .body())
                                .path("items")
                                .findValuesAsString("given"))
                .containsExactly("Hans", "Anna", "Anna");
        assertThat(this.get("/api/v1/people?sort=age", admin).statusCode()).isEqualTo(400);
        assertThat(
                        JSON.readTree(
                                        this.get("/api/v1/people?q=M%C3%9CLLER" + token, admin)
                                                .body())
                                .path("items"))
                .hasSize(1);

        // Replacing drops what is left out.
        final HttpResponse<String> replaced =
                this.call(
                        "PUT",
                        path,
                        admin,
                        "{\"names\": [], \"sex\": \"unknown\"}",
                        "If-Match",
                        created.headers().firstValue("ETag").orElseThrow());
        assertThat(replaced.statusCode()).as(replaced.body()).isEqualTo(200);
        assertThat(JSON.readTree(replaced.body()).path("names")).isEmpty();
        assertThat(JSON.readTree(replaced.body()).path("notes").isNull()).isTrue();
        final HttpResponse<String> invalid =
                this.call(
                        "PUT",
                        path,
                        admin,
                        "{\"names\": [{\"type\": \"birth\", \"nickname\": \"Bo\"}], \"sex\": \"unknown\"}",
                        "If-Match",
                        replaced.headers().firstValue("ETag").orElseThrow());
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(this.call("POST", "/api/v1/people", admin, "{\"names\": []}").statusCode())
                .isEqualTo(400);

        assertThat(
                        this.call(
                                        "DELETE",
                                        path,
                                        admin,
                                        null,
                                        "If-Match",
                                        replaced.headers().firstValue("ETag").orElseThrow())
                                .statusCode())
                .isEqualTo(204);
        final JsonNode history = JSON.readTree(this.get(path + "/history", admin).body());
        assertThat(history.findValuesAsString("kind"))
                .containsExactly("created", "changed", "deleted");
        assertThat(
                        history.get(0)
                                .path("changes")
                                .get(0)
                                .path("after")
                                .get(1)
                                .path("surname")
                                .asString())
                .isEqualTo("Müller" + token);
        assertThat(
                        JSON.readTree(
                                        this.get(
                                                        "/api/v1/audit?entityType=person&entityId="
                                                                + person.path("id").asString(),
                                                        admin)
                                                .body())
                                .path("items"))
                .hasSize(3);
    }

    @Test
    void familiesAndEventsLifecycle() throws Exception {
        this.completeSetupIfPending();
        final String admin = this.signInAsAdmin("it-families-admin");
        this.enableFeatures(admin, "people", "places", "families", "events", "audit-log");
        final String mother =
                this.createId(admin, "/api/v1/people", "{\"names\": [], \"sex\": \"female\"}");
        final String child =
                this.createId(admin, "/api/v1/people", "{\"names\": [], \"sex\": \"male\"}");
        final String church =
                this.createId(
                        admin,
                        "/api/v1/places",
                        "{\"name\": \"St. Anna\", \"type\": \"building\"}");
        final String family =
                this.createId(
                        admin,
                        "/api/v1/families",
                        "{\"partners\": [\""
                                + mother
                                + "\"], \"relationshipType\": \"married\", \"children\": [{\"person\": \""
                                + child
                                + "\", \"relation\": \"birth\"}]}");
        final HttpResponse<String> created =
                this.call(
                        "POST",
                        "/api/v1/events",
                        admin,
                        "{\"type\": \"birth\", \"date\": {\"modifier\": \"about\", \"start\": {\"year\": 1890,"
                                + " \"month\": 5}, \"text\": \"abt. May 1890\"}, \"place\": \""
                                + church
                                + "\", \"family\": \""
                                + family
                                + "\", \"participants\": [{\"person\": \""
                                + child
                                + "\", \"role\": \"principal\"}, {\"person\": \""
                                + mother
                                + "\", \"role\": \"other\"}]}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        final JsonNode birth = JSON.readTree(created.body());
        final String birthPath = "/api/v1/events/" + birth.path("id").asString();
        assertThat(birth.path("date").path("start").path("month").asInt()).isEqualTo(5);
        assertThat(birth.path("date").path("start").path("day").isNull()).isTrue();
        assertThat(birth.path("date").path("text").asString()).isEqualTo("abt. May 1890");

        // Dates that are only text, invalid dates and unknown people.
        assertThat(
                        this.call(
                                        "POST",
                                        "/api/v1/events",
                                        admin,
                                        "{\"type\": \"custom\", \"customType\": \"Harvest\", \"date\":"
                                                + " {\"modifier\": \"exact\", \"text\": \"after the flood\"},"
                                                + " \"participants\": []}")
                                .statusCode())
                .isEqualTo(201);
        for (final String invalid :
                List.of(
                        "{\"type\": \"birth\", \"date\": {\"modifier\": \"exact\", \"start\": {\"year\": 1890,"
                                + " \"month\": 2, \"day\": 30}}, \"participants\": []}",
                        "{\"type\": \"birth\", \"participants\": [{\"person\": \""
                                + UUID.randomUUID()
                                + "\", \"role\": \"principal\"}]}",
                        "{\"type\": \"custom\", \"participants\": []}")) {
            final HttpResponse<String> response =
                    this.call("POST", "/api/v1/events", admin, invalid);
            assertThat(response.statusCode()).as(invalid).isEqualTo(400);
            assertThat(problemType(response)).isEqualTo("urn:dulo:problem:validation");
        }

        // Lists, filtered.
        assertThat(
                        JSON.readTree(this.get("/api/v1/events?person=" + child, admin).body())
                                .path("items")
                                .findValuesAsString("id"))
                .containsExactly(birth.path("id").asString());
        assertThat(
                        JSON.readTree(
                                        this.get("/api/v1/events?type=birth&place=" + church, admin)
                                                .body())
                                .path("items"))
                .hasSize(1);
        assertThat(
                        JSON.readTree(this.get("/api/v1/families?member=" + child, admin).body())
                                .path("items")
                                .findValuesAsString("relationshipType"))
                .containsExactly("married");

        // Replacing the date with a range.
        final HttpResponse<String> range =
                this.call(
                        "PUT",
                        birthPath,
                        admin,
                        "{\"type\": \"birth\", \"date\": {\"modifier\": \"between\", \"start\": {\"year\":"
                                + " 1890}, \"end\": {\"year\": 1891}}, \"place\": \""
                                + church
                                + "\", \"family\": \""
                                + family
                                + "\", \"participants\": [{\"person\": \""
                                + child
                                + "\", \"role\": \"principal\"}, {\"person\": \""
                                + mother
                                + "\", \"role\": \"other\"}]}",
                        "If-Match",
                        "\"0\"");
        assertThat(range.statusCode()).as(range.body()).isEqualTo(200);
        assertThat(JSON.readTree(range.body()).path("date").path("end").path("year").asInt())
                .isEqualTo(1891);

        // Deleting the mother removes her from the family and the birth.
        final HttpResponse<String> linked =
                this.call("DELETE", "/api/v1/people/" + mother, admin, null, "If-Match", "\"0\"");
        assertThat(linked.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(linked.body()).path("links").findValuesAsString("field"))
                .containsExactly("partners", "participants");
        assertThat(
                        this.call(
                                        "DELETE",
                                        "/api/v1/people/" + mother + "?confirmed=true",
                                        admin,
                                        null,
                                        "If-Match",
                                        "\"0\"")
                                .statusCode())
                .isEqualTo(204);
        assertThat(
                        JSON.readTree(this.get("/api/v1/families/" + family, admin).body())
                                .path("partners"))
                .isEmpty();
        assertThat(
                        JSON.readTree(this.get(birthPath, admin).body())
                                .path("participants")
                                .findValuesAsString("person"))
                .containsExactly(child);

        // Deleting the family and the church leaves the birth without them.
        final String familyETag =
                this.get("/api/v1/families/" + family, admin)
                        .headers()
                        .firstValue("ETag")
                        .orElseThrow();
        assertThat(
                        this.call(
                                        "DELETE",
                                        "/api/v1/families/" + family,
                                        admin,
                                        null,
                                        "If-Match",
                                        familyETag)
                                .statusCode())
                .isEqualTo(409);
        assertThat(
                        this.call(
                                        "DELETE",
                                        "/api/v1/families/" + family + "?confirmed=true",
                                        admin,
                                        null,
                                        "If-Match",
                                        familyETag)
                                .statusCode())
                .isEqualTo(204);
        assertThat(
                        this.call(
                                        "DELETE",
                                        "/api/v1/places/" + church + "?confirmed=true",
                                        admin,
                                        null,
                                        "If-Match",
                                        "\"0\"")
                                .statusCode())
                .isEqualTo(204);
        final HttpResponse<String> orphan = this.get(birthPath, admin);
        assertThat(JSON.readTree(orphan.body()).path("family").isNull()).isTrue();
        assertThat(JSON.readTree(orphan.body()).path("place").isNull()).isTrue();

        // Events need no confirmation, and their history outlives them.
        assertThat(
                        this.call(
                                        "DELETE",
                                        birthPath,
                                        admin,
                                        null,
                                        "If-Match",
                                        orphan.headers().firstValue("ETag").orElseThrow())
                                .statusCode())
                .isEqualTo(204);
        assertThat(
                        JSON.readTree(this.get(birthPath + "/history", admin).body())
                                .findValuesAsString("kind"))
                .containsExactly("created", "changed", "changed", "changed", "changed", "deleted");
    }

    @Test
    void restoreLifecycle() throws Exception {
        this.completeSetupIfPending();
        final String admin = this.signInAsAdmin("it-restore-admin");
        this.enableFeatures(admin, "people", "families", "events", "audit-log");
        final String anna =
                "{\"names\": [{\"type\": \"birth\", \"given\": \"Anna\", \"surname\": \"Weber\"}],"
                        + " \"sex\": \"female\"}";
        final String mother = this.createId(admin, "/api/v1/people", anna);
        final String child =
                this.createId(admin, "/api/v1/people", "{\"names\": [], \"sex\": \"male\"}");
        final String family =
                this.createId(
                        admin,
                        "/api/v1/families",
                        "{\"partners\": [\""
                                + mother
                                + "\"], \"relationshipType\": \"married\", \"children\": [{\"person\": \""
                                + child
                                + "\", \"relation\": \"adopted\"}]}");
        final String birth =
                this.createId(
                        admin,
                        "/api/v1/events",
                        "{\"type\": \"birth\", \"participants\": [{\"person\": \""
                                + child
                                + "\", \"role\": \"principal\"}, {\"person\": \""
                                + mother
                                + "\", \"role\": \"witness\"}]}");
        final String path = "/api/v1/people/" + mother;
        assertThat(
                        this.call(
                                        "PUT",
                                        path,
                                        admin,
                                        anna.replace("Anna", "Anne"),
                                        "If-Match",
                                        "\"0\"")
                                .statusCode())
                .isEqualTo(200);
        final String creation =
                JSON.readTree(this.get(path + "/history", admin).body())
                        .get(0)
                        .path("id")
                        .asString();
        final String restore = "{\"eventId\": \"" + creation + "\"}";

        // An existing person needs If-Match.
        final HttpResponse<String> noVersion = this.call("POST", path + "/restore", admin, restore);
        assertThat(noVersion.statusCode()).isEqualTo(428);
        assertThat(problemType(noVersion)).isEqualTo("urn:dulo:problem:precondition-required");
        final HttpResponse<String> restored =
                this.call("POST", path + "/restore", admin, restore, "If-Match", "\"1\"");
        assertThat(restored.statusCode()).as(restored.body()).isEqualTo(200);
        assertThat(restored.headers().firstValue("ETag")).hasValue("\"2\"");
        assertThat(JSON.readTree(restored.body()).path("names").get(0).path("given").asString())
                .isEqualTo("Anna");

        // A deleted person comes back into the family and the event.
        assertThat(
                        this.call(
                                        "DELETE",
                                        path + "?confirmed=true",
                                        admin,
                                        null,
                                        "If-Match",
                                        "\"2\"")
                                .statusCode())
                .isEqualTo(204);
        final HttpResponse<String> undeleted = this.call("POST", path + "/restore", admin, restore);
        assertThat(undeleted.statusCode()).as(undeleted.body()).isEqualTo(200);
        assertThat(
                        JSON.readTree(this.get("/api/v1/families/" + family, admin).body())
                                .path("partners")
                                .get(0)
                                .asString())
                .isEqualTo(mother);
        final JsonNode participants =
                JSON.readTree(this.get("/api/v1/events/" + birth, admin).body())
                        .path("participants");
        assertThat(participants.findValuesAsString("person")).containsExactly(child, mother);
        assertThat(participants.get(1).path("role").asString()).isEqualTo("witness");
        assertThat(
                        JSON.readTree(this.get(path + "/history", admin).body())
                                .findValuesAsString("kind"))
                .containsExactly("created", "changed", "restored", "deleted", "restored");

        // Links that can't come back need confirmation: here, to an event deleted since.
        assertThat(
                        this.call(
                                        "DELETE",
                                        path + "?confirmed=true",
                                        admin,
                                        null,
                                        "If-Match",
                                        undeleted.headers().firstValue("ETag").orElseThrow())
                                .statusCode())
                .isEqualTo(204);
        final HttpResponse<String> event = this.get("/api/v1/events/" + birth, admin);
        assertThat(
                        this.call(
                                        "DELETE",
                                        "/api/v1/events/" + birth,
                                        admin,
                                        null,
                                        "If-Match",
                                        event.headers().firstValue("ETag").orElseThrow())
                                .statusCode())
                .isEqualTo(204);
        final HttpResponse<String> conflict = this.call("POST", path + "/restore", admin, restore);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(problemType(conflict)).isEqualTo("urn:dulo:problem:restore-conflict");
        assertThat(JSON.readTree(conflict.body()).path("links").findValuesAsString("entityId"))
                .containsExactly(birth);
        assertThat(this.call("POST", path + "/restore?confirmed=true", admin, restore).statusCode())
                .isEqualTo(200);

        // The change must be one of the person's, and not a deletion.
        final JsonNode history = JSON.readTree(this.get(path + "/history", admin).body());
        final String deletion = history.get(3).path("id").asString();
        assertThat(
                        this.call(
                                        "POST",
                                        "/api/v1/people/" + child + "/restore",
                                        admin,
                                        restore,
                                        "If-Match",
                                        "\"0\"")
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        this.call(
                                        "POST",
                                        path + "/restore",
                                        admin,
                                        "{\"eventId\": \"" + deletion + "\"}",
                                        "If-Match",
                                        "\"0\"")
                                .statusCode())
                .isEqualTo(400);
        assertThat(this.call("POST", path + "/restore", admin, "{}").statusCode()).isEqualTo(400);
    }

    @Test
    void changeEventsRoundTrip() {
        final String placeId = UUID.randomUUID().toString();
        final UserId actor = UserId.newId();
        final Instant at = Instant.parse("2026-11-17T10:15:30.123Z");
        final ChangeEvent event =
                new ChangeEvent(
                        ChangeEventId.newId(),
                        0,
                        at,
                        actor,
                        EntityType.PLACE,
                        placeId,
                        3,
                        ChangeKind.DELETED,
                        UUID.randomUUID(),
                        List.of(
                                new FieldChange("name", "Plovdiv – Пловдив", null),
                                new FieldChange(
                                        "coordinates",
                                        Map.of("lat", 42.1354, "lon", 24.7453),
                                        null),
                                new FieldChange("tags", List.of("a", List.of(1L, true)), null)),
                        List.of(new Link(EntityType.EVENT, UUID.randomUUID().toString(), "place")));

        final ChangeEvent stored = unitOfWork.write(() -> changeEvents.append(event));
        final ChangeEvent later = unitOfWork.write(() -> changeEvents.append(copyWithNewId(event)));

        assertThat(stored.sequence()).isPositive();
        assertThat(later.sequence()).isGreaterThan(stored.sequence());
        assertThat(unitOfWork.read(() -> changeEvents.history(EntityType.PLACE, placeId)))
                .containsExactly(stored, later);
        final ChangeEventQuery query =
                new ChangeEventQuery(
                        Set.of(EntityType.PLACE), placeId, actor, at, at.plusMillis(1));
        assertThat(unitOfWork.read(() -> changeEvents.find(query, null, 10)))
                .containsExactly(later, stored);
        assertThat(unitOfWork.read(() -> changeEvents.find(query, later.sequence(), 10)))
                .containsExactly(stored);
        assertThat(unitOfWork.read(() -> changeEvents.find(query, null, 1))).containsExactly(later);
        assertThatThrownBy(() -> unitOfWork.write(() -> changeEvents.append(stored)))
                .isInstanceOf(IllegalArgumentException.class);
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

    /** Signs in a user whom the identity provider puts in the administrators' group. */
    private String signInAsAdmin(final String username) throws Exception {
        return this.client().signIn(username, "{\"groups\": [\"family-admins\"]}").accessToken();
    }

    /** Creates a record and returns its id. */
    private String createId(final String token, final String path, final String json)
            throws Exception {
        final HttpResponse<String> created = this.call("POST", path, token, json);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return JSON.readTree(created.body()).path("id").asString();
    }

    /** Switches exactly these features on, as an administrator. */
    private void enableFeatures(final String admin, final String... features) throws Exception {
        final String etag =
                this.get("/api/v1/tree", admin).headers().firstValue("ETag").orElseThrow();
        final HttpResponse<String> changed =
                this.call(
                        "PATCH",
                        "/api/v1/tree",
                        admin,
                        JSON.writeValueAsString(Map.of("enabledFeatures", List.of(features))),
                        "If-Match",
                        etag);
        assertThat(changed.statusCode()).as(changed.body()).isEqualTo(200);
    }

    /** A logout token for {@link #BACK_CHANNEL_USER} from the identity provider. */
    private String logoutToken() throws IOException, InterruptedException {
        final HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://localhost:"
                                                        + IDP.getMappedPort(8080)
                                                        + "/default/token"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                "grant_type=client_credentials&client_id=dulo"
                                                        + "&client_secret=mock-secret"
                                                        + "&scope=back-channel-logout"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body()).path("access_token").asString();
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
