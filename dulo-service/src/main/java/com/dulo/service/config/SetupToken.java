package com.dulo.service.config;

import com.dulo.core.setup.port.out.SetupTokenPort;

import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Base64;

/** The setup token from {@code dulo.setup.token}, or a random one made on start. */
class SetupToken implements SetupTokenPort {

    /** 256 bits. */
    private static final int RANDOM_BYTES = 32;

    private final String token;
    private final boolean generated;

    SetupToken(final @Nullable String configured, final SecureRandom random) {
        if (configured == null || configured.isBlank()) {
            final byte[] bytes = new byte[RANDOM_BYTES];
            random.nextBytes(bytes);
            this.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            this.generated = true;
        } else {
            this.token = configured;
            this.generated = false;
        }
    }

    @Override
    public String token() {
        return this.token;
    }

    /** True when the token was made on start, so the log is the only place to find it. */
    boolean generated() {
        return this.generated;
    }
}
