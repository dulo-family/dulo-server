package com.dulo.adapter.rest.identity;

import com.dulo.domain.identity.IdentityProvider;

import io.swagger.v3.oas.annotations.media.Schema;

record IdentityProviderResponse(
        String id,
        @Schema(description = "Name for the sign-in button.") String displayName,
        @Schema(allowableValues = {"oidc", "google", "microsoft"}) String type,
        @Schema(
                        description =
                                "Path that starts the sign-in with this provider. Open it in the"
                                        + " browser during an authorization request.",
                        example = "/oauth2/authorization/google")
                String signInPath) {

    static IdentityProviderResponse from(final IdentityProvider provider) {
        return new IdentityProviderResponse(
                provider.id(),
                provider.displayName(),
                provider.type().key(),
                "/oauth2/authorization/" + provider.id());
    }
}
