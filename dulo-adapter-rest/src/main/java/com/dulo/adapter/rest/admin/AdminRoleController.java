package com.dulo.adapter.rest.admin;

import com.dulo.adapter.rest.access.RoleResponse;
import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.adapter.rest.version.Versions;
import com.dulo.core.access.port.in.ManageRolesUseCase;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@Tag(
        name = "Roles",
        description =
                "Administration of the roles and their permissions. Needs the administration"
                        + " feature with the matching access. Only the root user may change the"
                        + " built-in roles, and nobody may delete them.")
@RestController
@RequestMapping("/api/v1/admin/roles")
@SecurityRequirement(name = SecuritySchemes.OAUTH2)
@ApiResponse(
        responseCode = "403",
        description =
                "The caller may not do this: they lack the access, the role is built in and"
                        + " they aren't root, or the role grants more than they may do"
                        + " themselves. The problem type is `urn:dulo:problem:forbidden`, or says"
                        + " why the account is not active.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
class AdminRoleController {

    private static final String BASE = "/api/v1/admin/roles";

    private final ManageRolesUseCase roles;

    AdminRoleController(final ManageRolesUseCase roles) {
        this.roles = roles;
    }

    private static ResponseEntity<RoleResponse> withETag(final Role role) {
        return ResponseEntity.ok()
                .eTag(Versions.etag(role.version()))
                .body(RoleResponse.from(role));
    }

    @Operation(summary = "List the roles", description = "Sorted by key.")
    @ApiResponse(responseCode = "200", description = "The roles")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    List<RoleResponse> roles(final Principal principal) {
        return this.roles.roles(Callers.id(principal)).stream().map(RoleResponse::from).toList();
    }

    @Operation(summary = "Get a role")
    @ApiResponse(
            responseCode = "200",
            description = "The role",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "Version of the role",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description = "No such role. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RoleResponse> role(final Principal principal, @PathVariable final String key) {
        return withETag(this.roles.role(Callers.id(principal), new RoleKey(key)));
    }

    @Operation(summary = "Create a role")
    @ApiResponse(responseCode = "201", description = "The new role. Its address is in Location.")
    @ApiResponse(
            responseCode = "400",
            description =
                    "Invalid role, e.g. an invalid key or an unknown feature. The problem type is `urn:dulo:problem:validation`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "A role with the key exists. The problem type is `urn:dulo:problem:role-exists`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RoleResponse> createRole(
            final Principal principal, @RequestBody final RoleCreateRequest request) {
        final Role role = this.roles.createRole(Callers.id(principal), request.toNewRole());
        return ResponseEntity.created(URI.create(BASE + "/" + role.key()))
                .eTag(Versions.etag(role.version()))
                .body(RoleResponse.from(role));
    }

    @Operation(summary = "Change a role", description = "Left-out values stay as they are.")
    @ApiResponse(
            responseCode = "200",
            description = "The changed role",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "New version of the role",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "400",
            description =
                    "Invalid change, e.g. an unknown feature. The problem type is `urn:dulo:problem:validation`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No such role. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The role changed since it was read. The problem type is `urn:dulo:problem:concurrent-modification`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "428",
            description =
                    "If-Match is missing. The problem type is `urn:dulo:problem:precondition-required`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping(
            path = "/{key}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RoleResponse> updateRole(
            final Principal principal,
            @PathVariable final String key,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch,
            @RequestBody final RoleChangeRequest request) {
        return withETag(
                this.roles.updateRole(
                        Callers.id(principal),
                        new RoleKey(key),
                        Versions.fromIfMatch(ifMatch),
                        request.toChange()));
    }

    @Operation(
            summary = "Delete a role",
            description = "Only roles nobody has and no identity provider maps to.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(
            responseCode = "400",
            description =
                    "Built-in roles can't be deleted. The problem type is `urn:dulo:problem:validation`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No such role. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "Users have the role, or an identity provider maps to it. The problem type is"
                            + " `urn:dulo:problem:role-in-use`. Its `users` member lists the ids"
                            + " of the users who have it, and `identityProviders` the providers"
                            + " whose configuration maps to it.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The role changed since it was read. The problem type is `urn:dulo:problem:concurrent-modification`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "428",
            description =
                    "If-Match is missing. The problem type is `urn:dulo:problem:precondition-required`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping(path = "/{key}")
    ResponseEntity<Void> deleteRole(
            final Principal principal,
            @PathVariable final String key,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch) {
        this.roles.deleteRole(
                Callers.id(principal), new RoleKey(key), Versions.fromIfMatch(ifMatch));
        return ResponseEntity.noContent().build();
    }
}
