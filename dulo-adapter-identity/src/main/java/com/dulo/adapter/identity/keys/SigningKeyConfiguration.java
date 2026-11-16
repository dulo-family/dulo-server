package com.dulo.adapter.identity.keys;

import com.dulo.core.identity.port.in.SigningKeyUseCase;
import com.dulo.domain.identity.SigningKey;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.text.ParseException;
import java.time.Clock;
import java.util.UUID;

/**
 * The RSA key Dulo signs its tokens with. It is created on the first start and kept in the
 * database, so tokens stay valid across restarts.
 */
@Configuration(proxyBeanMethods = false)
public class SigningKeyConfiguration {

    private static final int KEY_SIZE = 2048;

    static SigningKey generate(final Clock clock) {
        try {
            final RSAKey key =
                    new RSAKeyGenerator(KEY_SIZE)
                            .keyID(UUID.randomUUID().toString())
                            .keyUse(KeyUse.SIGNATURE)
                            .generate();
            return new SigningKey(key.getKeyID(), key.toJSONString(), clock.instant());
        } catch (final JOSEException e) {
            throw new IllegalStateException("Cannot generate the token signing key", e);
        }
    }

    static RSAKey parse(final SigningKey key) {
        try {
            return RSAKey.parse(key.material());
        } catch (final ParseException e) {
            throw new IllegalStateException("The stored token signing key is invalid", e);
        }
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(final SigningKeyUseCase signingKeys, final Clock clock) {
        final RSAKey key = parse(signingKeys.currentOrCreate(() -> generate(clock)));
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    /** Checks Dulo's own access tokens locally, without asking the authorization server. */
    @Bean
    JwtDecoder jwtDecoder(final JWKSource<SecurityContext> jwkSource) {
        return NimbusJwtDecoder.withJwkSource(jwkSource).build();
    }
}
