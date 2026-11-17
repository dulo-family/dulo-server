package com.dulo.adapter.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicEndpointsBearerTokenResolverTest {

    private final PublicEndpointsBearerTokenResolver resolver =
            new PublicEndpointsBearerTokenResolver(ApiSecurityConfiguration.PUBLIC_ENDPOINTS);

    @ParameterizedTest
    @CsvSource({
        "GET, /api/v1/identity-providers, false",
        "GET, /api/v1/openapi.json, false",
        "GET, /api/v1/setup/status, false",
        "GET, /api/v1/setup/defaults, false",
        // Completing the setup needs the signed-in user.
        "POST, /api/v1/setup, true",
        "GET, /api/v1/tree, true",
        "GET, /api/v1/me, true",
    })
    void ignoresTokensOnlyOnPublicEndpoints(
            final String method, final String path, final boolean resolved) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer expired-token");

        assertThat(this.resolver.resolve(request)).isEqualTo(resolved ? "expired-token" : null);
    }
}
