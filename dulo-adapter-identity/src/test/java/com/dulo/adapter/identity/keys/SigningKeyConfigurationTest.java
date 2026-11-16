package com.dulo.adapter.identity.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.domain.identity.SigningKey;
import com.nimbusds.jose.jwk.RSAKey;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class SigningKeyConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

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
}
