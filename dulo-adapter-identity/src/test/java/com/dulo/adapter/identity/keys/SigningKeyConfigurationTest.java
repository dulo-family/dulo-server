package com.dulo.adapter.identity.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.domain.identity.SigningKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

class SigningKeyConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final String ISSUER = "http://localhost:8080";

    private final JWKSource<SecurityContext> keys =
            new ImmutableJWKSet<>(
                    new JWKSet(
                            SigningKeyConfiguration.parse(
                                    SigningKeyConfiguration.generate(Clock.systemUTC()))));
    private final JwtDecoder decoder =
            new SigningKeyConfiguration()
                    .jwtDecoder(
                            this.keys,
                            AuthorizationServerSettings.builder().issuer(ISSUER).build());

    /** A token as Dulo's authorization server signs it. */
    private String token(
            final @Nullable String type,
            final String issuer,
            final String audience,
            final @Nullable String clientId) {
        final JwsHeader.Builder header = JwsHeader.with(SignatureAlgorithm.RS256);
        if (type != null) {
            header.type(type);
        }
        final Instant now = Instant.now();
        final JwtClaimsSet.Builder claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .subject("user")
                        .audience(List.of(audience))
                        .issuedAt(now)
                        .expiresAt(now.plus(Duration.ofMinutes(15)))
                        .id(UUID.randomUUID().toString());
        if (clientId != null) {
            claims.claim("client_id", clientId);
        }
        return new NimbusJwtEncoder(this.keys)
                .encode(JwtEncoderParameters.from(header.build(), claims.build()))
                .getTokenValue();
    }

    @Test
    void generatedKeysSurviveStorage() {
        final SigningKey stored =
                SigningKeyConfiguration.generate(Clock.fixed(NOW, ZoneOffset.UTC));

        final RSAKey key = SigningKeyConfiguration.parse(stored);

        assertThat(stored.createdAt()).isEqualTo(NOW);
        assertThat(key.getKeyID()).isEqualTo(stored.id());
        assertThat(key.isPrivate()).isTrue();
        assertThat(key.size()).isEqualTo(2048);
    }

    @Test
    void rejectsBrokenKeyMaterial() {
        assertThatThrownBy(
                        () -> SigningKeyConfiguration.parse(new SigningKey("k", "not json", NOW)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsAccessTokens() {
        assertThat(
                        this.decoder
                                .decode(this.token("at+jwt", ISSUER, ISSUER, "dulo-web"))
                                .getSubject())
                .isEqualTo("user");
    }

    @Test
    void rejectsIdTokensAndOtherIssuers() {
        // ID tokens have the plain JWT type, or none, the client as the audience and no client_id.
        for (final String token :
                new String[] {
                    this.token("JWT", ISSUER, "dulo-web", null),
                    this.token(null, ISSUER, "dulo-web", null),
                    this.token("JWT", ISSUER, ISSUER, "dulo-web"),
                    this.token("at+jwt", ISSUER, "dulo-web", "dulo-web"),
                    this.token("at+jwt", ISSUER, ISSUER, null),
                    this.token("at+jwt", "http://elsewhere", ISSUER, "dulo-web"),
                }) {
            assertThatThrownBy(() -> this.decoder.decode(token)).isInstanceOf(JwtException.class);
        }
    }
}
