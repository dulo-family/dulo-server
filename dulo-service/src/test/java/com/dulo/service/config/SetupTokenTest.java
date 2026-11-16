package com.dulo.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

class SetupTokenTest {

    @Test
    void usesTheConfiguredToken() {
        final SetupToken token = new SetupToken("from-config", new SecureRandom());

        assertThat(token.token()).isEqualTo("from-config");
        assertThat(token.generated()).isFalse();
    }

    @Test
    void generatesARandomTokenOtherwise() {
        final SetupToken first = new SetupToken(null, new SecureRandom());
        final SetupToken second = new SetupToken("  ", new SecureRandom());

        assertThat(first.generated()).isTrue();
        assertThat(second.generated()).isTrue();
        // 32 random bytes, base64url without padding.
        assertThat(first.token()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first.token()).isNotEqualTo(second.token());
    }
}
