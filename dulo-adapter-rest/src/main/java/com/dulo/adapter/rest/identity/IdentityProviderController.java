package com.dulo.adapter.rest.identity;

import com.dulo.core.identity.port.in.ListIdentityProvidersUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Identity providers", description = "Where users can sign in")
@RestController
@RequestMapping("/api/v1/identity-providers")
class IdentityProviderController {

    private final ListIdentityProvidersUseCase providers;

    IdentityProviderController(final ListIdentityProvidersUseCase providers) {
        this.providers = providers;
    }

    @Operation(
            summary = "List the identity providers",
            description =
                    "Public, for the sign-in page. Sorted by id. When an authorization request"
                            + " names one with the `idp` parameter, or only one is configured,"
                            + " the server skips the sign-in page and goes straight to it.")
    @ApiResponse(responseCode = "200", description = "The configured identity providers")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    List<IdentityProviderResponse> identityProviders() {
        return this.providers.identityProviders().stream()
                .map(IdentityProviderResponse::from)
                .toList();
    }
}
