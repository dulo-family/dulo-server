package com.dulo.adapter.rest.admin;

import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.adapter.rest.version.Versions;
import com.dulo.core.access.port.in.ManageUsersUseCase;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@Tag(
        name = "Users",
        description =
                "Administration of the users: approve, disable, assign roles. Needs the"
                        + " administration feature: view to read, edit to change.")
@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = SecuritySchemes.OAUTH2)
@ApiResponse(
        responseCode = "403",
        description =
                "The caller may not do this: they lack the access, or, unless they are root,"
                        + " the user may do more than they may, or an added role grants more than"
                        + " they have. The problem type is `urn:dulo:problem:forbidden`, or says"
                        + " why the account is not active.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
class AdminUserController {

    private final ManageUsersUseCase users;

    AdminUserController(final ManageUsersUseCase users) {
        this.users = users;
    }

    private static ResponseEntity<AdminUserResponse> withETag(final User user) {
        return ResponseEntity.ok()
                .eTag(Versions.etag(user.version()))
                .body(AdminUserResponse.from(user));
    }

    @Operation(summary = "List the users", description = "Oldest first.")
    @ApiResponse(responseCode = "200", description = "The users")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    List<AdminUserResponse> users(final Principal principal) {
        return this.users.users(Callers.id(principal)).stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @Operation(summary = "Get a user")
    @ApiResponse(
            responseCode = "200",
            description = "The user",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "Version of the user",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description = "No such user. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AdminUserResponse> user(
            final Principal principal, @PathVariable final String id) {
        return withETag(this.users.user(Callers.id(principal), UserId.parse(id)));
    }

    @Operation(
            summary = "Change a user",
            description =
                    "Approves, disables or reactivates the user, or replaces the roles an"
                            + " administrator assigned. Nobody can change their own status or the"
                            + " root user's.")
    @ApiResponse(
            responseCode = "200",
            description = "The changed user",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "New version of the user",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "400",
            description =
                    "Invalid change, e.g. an unknown role. The problem type is `urn:dulo:problem:validation`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No such user. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The user changed since it was read. The problem type is `urn:dulo:problem:concurrent-modification`.",
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
            path = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AdminUserResponse> updateUser(
            final Principal principal,
            @PathVariable final String id,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch,
            @RequestBody final UserChangeRequest request) {
        return withETag(
                this.users.updateUser(
                        Callers.id(principal),
                        UserId.parse(id),
                        Versions.fromIfMatch(ifMatch),
                        request.toChange()));
    }
}
