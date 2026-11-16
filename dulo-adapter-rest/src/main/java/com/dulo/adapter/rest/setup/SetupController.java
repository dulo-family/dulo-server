package com.dulo.adapter.rest.setup;

import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.adapter.rest.tree.TreeResponse;
import com.dulo.core.setup.port.in.CompleteSetupUseCase;
import com.dulo.core.setup.port.in.GetSetupConfigurationUseCase;
import com.dulo.core.setup.port.in.GetSetupDefaultsUseCase;
import com.dulo.core.setup.port.in.GetSetupStatusUseCase;
import com.dulo.core.tree.port.in.TreeView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;

@Tag(
        name = "Setup",
        description =
                "The one-time initial setup. Until it's done, the rest of the API answers 503. The"
                        + " setup token is in the server log, or in dulo.setup.token.")
@RestController
@RequestMapping("/api/v1/setup")
class SetupController {

    static final String TOKEN_HEADER = "X-Dulo-Setup-Token";

    private static final String TOKEN_DESCRIPTION =
            "The one-time setup token. The server logs it on start, unless it is set in"
                    + " dulo.setup.token.";

    private final GetSetupStatusUseCase status;
    private final GetSetupDefaultsUseCase defaults;
    private final GetSetupConfigurationUseCase configuration;
    private final CompleteSetupUseCase complete;

    SetupController(
            final GetSetupStatusUseCase status,
            final GetSetupDefaultsUseCase defaults,
            final GetSetupConfigurationUseCase configuration,
            final CompleteSetupUseCase complete) {
        this.status = status;
        this.defaults = defaults;
        this.configuration = configuration;
        this.complete = complete;
    }

    /** A missing header is as wrong as a wrong token. */
    private static String token(final @Nullable String header) {
        return header == null ? "" : header;
    }

    @Operation(summary = "Is the setup pending?", description = "Public.")
    @ApiResponse(responseCode = "200", description = "Whether the setup is pending")
    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    SetupStatusResponse status() {
        return new SetupStatusResponse(this.status.isSetupPending());
    }

    @Operation(
            summary = "Get what the setup starts from",
            description =
                    "The built-in roles, the features, and the identity providers with their role"
                            + " mappings.")
    @ApiResponse(responseCode = "200", description = "The defaults")
    @ApiResponse(
            responseCode = "403",
            description =
                    "Missing or wrong setup token. The problem type is"
                            + " `urn:dulo:problem:invalid-setup-token`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "The setup is done. The problem type is `urn:dulo:problem:setup-completed`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/defaults", produces = MediaType.APPLICATION_JSON_VALUE)
    SetupDefaultsResponse defaults(
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = TOKEN_HEADER,
                            description = TOKEN_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = TOKEN_HEADER, required = false)
                    final @Nullable String token) {
        return SetupDefaultsResponse.from(this.defaults.defaults(token(token)));
    }

    @Operation(
            summary = "Get the server's configuration",
            description =
                    "The merged configuration with secrets masked and the source of each value,"
                            + " to check it before completing the setup.")
    @ApiResponse(responseCode = "200", description = "The configuration")
    @ApiResponse(
            responseCode = "403",
            description =
                    "Missing or wrong setup token. The problem type is"
                            + " `urn:dulo:problem:invalid-setup-token`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "The setup is done. The problem type is `urn:dulo:problem:setup-completed`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    SetupConfigurationResponse configuration(
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = TOKEN_HEADER,
                            description = TOKEN_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = TOKEN_HEADER, required = false)
                    final @Nullable String token) {
        return new SetupConfigurationResponse(this.configuration.configuration(token(token)));
    }

    @Operation(
            summary = "Complete the setup",
            description =
                    "Creates the tree and the roles, and makes the signed-in caller the root user,"
                            + " who may do everything. Works once.")
    @SecurityRequirement(name = SecuritySchemes.OAUTH2)
    @ApiResponse(responseCode = "201", description = "The new tree. Its address is in Location.")
    @ApiResponse(
            responseCode = "400",
            description =
                    "Invalid request, e.g. an unknown feature or a role a provider maps to but the"
                            + " request doesn't define. The problem type is"
                            + " `urn:dulo:problem:validation`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "401",
            description =
                    "No valid access token. The problem type is `urn:dulo:problem:unauthorized`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "403",
            description =
                    "Missing or wrong setup token. The problem type is"
                            + " `urn:dulo:problem:invalid-setup-token`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "The setup is done. The problem type is `urn:dulo:problem:setup-completed`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TreeResponse> complete(
            final Principal principal,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = TOKEN_HEADER,
                            description = TOKEN_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = TOKEN_HEADER, required = false)
                    final @Nullable String token,
            @RequestBody final SetupRequest request) {
        final TreeView tree =
                this.complete.complete(Callers.id(principal), token(token), request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/tree"))
                .eTag(Long.toString(tree.tree().version()))
                .body(TreeResponse.from(tree));
    }
}
