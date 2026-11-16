package com.dulo.adapter.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.adapter.identity.settings.TestSettings;
import com.dulo.core.identity.port.in.ExternalSignIn;
import com.dulo.domain.user.ExternalIdentity;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class DuloOidcUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UserId USER_ID = UserId.newId();

    private final List<ExternalSignIn> signIns = new ArrayList<>();
    private final DuloOidcUserService service =
            new DuloOidcUserService(
                    _ ->
                            new DefaultOidcUser(
                                    List.of(),
                                    new OidcIdToken(
                                            "id-token",
                                            NOW,
                                            NOW.plusSeconds(60),
                                            Map.of(
                                                    "sub", "ada",
                                                    "email", "ada@example.com",
                                                    "name", "Ada",
                                                    "groups", List.of("family", "admins")))),
                    TestSettings.settings(TestSettings.provider("pocketid", "secret")),
                    signIn -> {
                        this.signIns.add(signIn);
                        return USER_ID;
                    });

    private static OidcUserRequest request(final String registrationId) {
        final ClientRegistration registration =
                ClientRegistration.withRegistrationId(registrationId)
                        .clientId("dulo")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("https://tree.example.com/login/oauth2/code/" + registrationId)
                        .authorizationUri("https://id.example.com/authorize")
                        .tokenUri("https://id.example.com/token")
                        .build();
        return new OidcUserRequest(
                registration,
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER, "access", NOW, NOW.plusSeconds(60)),
                new OidcIdToken("id-token", NOW, NOW.plusSeconds(60), Map.of("sub", "ada")));
    }

    @Test
    void signsTheUserInToDulo() {
        final OidcUser user = this.service.loadUser(request("pocketid"));

        assertThat(this.signIns)
                .containsExactly(
                        new ExternalSignIn(
                                new ExternalIdentity("pocketid", "ada"),
                                "ada@example.com",
                                "Ada",
                                Set.of("family", "admins")));
        assertThat(user.getName()).isEqualTo(USER_ID.toString());
        assertThat(user.getAuthorities()).anyMatch(FactorGrantedAuthority.class::isInstance);
    }

    @Test
    void rejectsUnknownProviders() {
        assertThatThrownBy(() -> this.service.loadUser(request("ghost")))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(this.signIns).isEmpty();
    }

    @Test
    void readsRoleClaimsInSeveralShapes() {
        assertThat(DuloOidcUserService.claimValues(List.of("a", "b")))
                .containsExactlyInAnyOrder("a", "b");
        assertThat(DuloOidcUserService.claimValues("a, b c"))
                .containsExactlyInAnyOrder("a", "b", "c");
        assertThat(DuloOidcUserService.claimValues("")).isEmpty();
        assertThat(DuloOidcUserService.claimValues(42)).isEmpty();
        assertThat(DuloOidcUserService.claimValues((@Nullable Object) null)).isEmpty();
    }

    @Test
    void usersAreEqualByDuloIdAndToken() {
        final OidcUser first = this.service.loadUser(request("pocketid"));
        final OidcUser second = this.service.loadUser(request("pocketid"));

        assertThat(first).isNotEqualTo(second); // different sign-in times in the factor authority
        assertThat(first).isEqualTo(first).hasSameHashCodeAs(first);
        assertThat(first).isNotEqualTo("ada");
    }
}
