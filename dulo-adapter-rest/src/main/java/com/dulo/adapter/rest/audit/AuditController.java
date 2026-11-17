package com.dulo.adapter.rest.audit;

import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.adapter.rest.paging.PageResponse;
import com.dulo.core.audit.port.in.QueryAuditLogUseCase;
import com.dulo.core.audit.port.in.QueryAuditLogUseCase.AuditFilter;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.user.UserId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;

@Tag(
        name = "Audit",
        description =
                "The audit history: every change to every record, with the values before and"
                        + " after. Needs view access to the audit-log feature; changes to the tree"
                        + " settings, roles and users also need view access to administration.")
@RestController
@RequestMapping("/api/v1/audit")
@SecurityRequirement(name = SecuritySchemes.OAUTH2)
class AuditController {

    private final QueryAuditLogUseCase auditLog;

    AuditController(final QueryAuditLogUseCase auditLog) {
        this.auditLog = auditLog;
    }

    private static @Nullable EntityType entityType(final @Nullable String key) {
        if (key == null) {
            return null;
        }
        return EntityType.fromKey(key)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown entity type '%s'".formatted(key)));
    }

    @Operation(
            summary = "List changes",
            description =
                    "Newest first, in pages. Without administration access, changes to the tree"
                            + " settings, roles and users are left out.")
    @ApiResponse(responseCode = "200", description = "One page of changes")
    @ApiResponse(
            responseCode = "400",
            description =
                    "An unknown entity type, an invalid cursor, limit or time range. The problem"
                            + " type is `urn:dulo:problem:validation`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "403",
            description =
                    "The caller may not view the audit log, or filters by an administrative type"
                            + " without administration access. The problem type is"
                            + " `urn:dulo:problem:forbidden`, `urn:dulo:problem:feature-disabled`,"
                            + " or says why the account is not active.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    PageResponse<ChangeEventResponse> changes(
            final Principal principal,
            @Parameter(
                            description = "Only changes to records of this type.",
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "event", "family", "person", "place", "role",
                                                "tree", "user"
                                            }))
                    @RequestParam(required = false)
                    final @Nullable String entityType,
            @Parameter(
                            description =
                                    "Only changes to the record with this id. Combine it with"
                                            + " `entityType`: role keys are ids too.")
                    @RequestParam(required = false)
                    final @Nullable String entityId,
            @Parameter(description = "Only changes by the user with this id.")
                    @RequestParam(required = false)
                    final @Nullable String actor,
            @Parameter(description = "Only changes at or after this time (ISO 8601).")
                    @RequestParam(required = false)
                    final @Nullable Instant from,
            @Parameter(description = "Only changes before this time (ISO 8601).")
                    @RequestParam(required = false)
                    final @Nullable Instant to,
            @Parameter(description = PageResponse.AFTER_DESCRIPTION) @RequestParam(required = false)
                    final @Nullable String after,
            @Parameter(description = PageResponse.LIMIT_DESCRIPTION)
                    @RequestParam(defaultValue = PageResponse.DEFAULT_LIMIT)
                    final int limit) {
        final AuditFilter filter =
                new AuditFilter(
                        entityType(entityType),
                        entityId,
                        actor == null ? null : UserId.parse(actor),
                        from,
                        to);
        return PageResponse.from(
                this.auditLog.changes(Callers.id(principal), filter, after, limit),
                ChangeEventResponse::from);
    }
}
