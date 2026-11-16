package com.dulo.adapter.rest.me;

import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.core.access.port.in.GetCurrentUserUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Tag(name = "Me", description = "The signed-in user")
@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = SecuritySchemes.OAUTH2)
class MeController {

    private final GetCurrentUserUseCase currentUser;

    MeController(final GetCurrentUserUseCase currentUser) {
        this.currentUser = currentUser;
    }

    @Operation(
            summary = "Get the signed-in user",
            description =
                    "Returns the user, their roles and what they may do. Also works while the"
                            + " account waits for approval or is disabled, so clients can say why.")
    @ApiResponse(responseCode = "200", description = "The signed-in user")
    @ApiResponse(
            responseCode = "401",
            description =
                    "No valid access token. The problem type is `urn:dulo:problem:unauthorized`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    MeResponse me(final Principal principal) {
        return MeResponse.from(this.currentUser.currentUser(Callers.id(principal)));
    }
}
