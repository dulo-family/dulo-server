package com.dulo.adapter.rest.server;

import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.core.server.port.in.GetServerInfoUseCase;

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

@Tag(name = "Server", description = "The Dulo server itself")
@RestController
@RequestMapping("/api/v1/server")
@SecurityRequirement(name = SecuritySchemes.OAUTH2)
class ServerController {

    private final GetServerInfoUseCase getServerInfo;

    ServerController(final GetServerInfoUseCase getServerInfo) {
        this.getServerInfo = getServerInfo;
    }

    @Operation(
            summary = "Get the server's version",
            description =
                    "Returns the version of the running server, for clients that show what is"
                            + " running. Every active user may read it.")
    @ApiResponse(responseCode = "200", description = "The server")
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
                    "The account is not active. The problem type is"
                            + " `urn:dulo:problem:account-pending` or"
                            + " `urn:dulo:problem:account-disabled`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "503",
            description =
                    "The initial setup isn't done yet. The problem type is"
                            + " `urn:dulo:problem:setup-required`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    ServerResponse getServer(final Principal principal) {
        return ServerResponse.from(this.getServerInfo.getServerInfo(Callers.id(principal)));
    }
}
