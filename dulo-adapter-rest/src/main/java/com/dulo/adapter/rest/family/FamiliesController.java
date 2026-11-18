package com.dulo.adapter.rest.family;

import com.dulo.adapter.rest.audit.ChangeEventResponse;
import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.adapter.rest.paging.PageResponse;
import com.dulo.adapter.rest.version.Versions;
import com.dulo.core.family.port.in.ManageFamiliesUseCase;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@Tag(
        name = "Families",
        description =
                "Partners and their children. Needs the families feature: view to read, create,"
                        + " edit and delete to change.")
@RestController
@RequestMapping("/api/v1/families")
@SecurityRequirement(name = SecuritySchemes.OAUTH2)
@ApiResponse(
        responseCode = "400",
        description =
                "An invalid id, body, cursor or limit. The problem type is"
                        + " `urn:dulo:problem:validation`.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "403",
        description =
                "The caller lacks the access, or the feature is off. The problem type is"
                        + " `urn:dulo:problem:forbidden`, `urn:dulo:problem:feature-disabled`, or"
                        + " says why the account is not active.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
class FamiliesController {

    private static final String BASE = "/api/v1/families";

    private final ManageFamiliesUseCase families;

    FamiliesController(final ManageFamiliesUseCase families) {
        this.families = families;
    }

    private static ResponseEntity<FamilyResponse> withETag(final Family family) {
        return ResponseEntity.ok()
                .eTag(Versions.etag(family.version()))
                .body(FamilyResponse.from(family));
    }

    @Operation(
            summary = "List families",
            description = "In the order they were created, to the millisecond, in pages.")
    @ApiResponse(responseCode = "200", description = "One page of families")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    PageResponse<FamilyResponse> families(
            final Principal principal,
            @Parameter(description = "Only families with this person as a partner or child.")
                    @RequestParam(required = false)
                    final @Nullable String member,
            @Parameter(description = PageResponse.AFTER_DESCRIPTION) @RequestParam(required = false)
                    final @Nullable String after,
            @Parameter(description = PageResponse.LIMIT_DESCRIPTION)
                    @RequestParam(defaultValue = PageResponse.DEFAULT_LIMIT)
                    final int limit) {
        return PageResponse.from(
                this.families.families(
                        Callers.id(principal),
                        member == null ? null : PersonId.parse(member),
                        after,
                        limit),
                FamilyResponse::from);
    }

    @Operation(summary = "Get a family")
    @ApiResponse(
            responseCode = "200",
            description = "The family",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "Version of the family",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description = "No such family. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FamilyResponse> family(
            final Principal principal, @PathVariable final String id) {
        return withETag(this.families.family(Callers.id(principal), FamilyId.parse(id)));
    }

    @Operation(summary = "Create a family", description = "The partners and children must exist.")
    @ApiResponse(
            responseCode = "201",
            description = "The new family",
            headers = {
                @Header(
                        name = HttpHeaders.LOCATION,
                        description = "Path of the family",
                        schema = @Schema(type = "string")),
                @Header(
                        name = HttpHeaders.ETAG,
                        description = "Version of the family",
                        schema = @Schema(type = "string"))
            })
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FamilyResponse> createFamily(
            final Principal principal, @RequestBody final FamilyRequest request) {
        final Family family =
                this.families.createFamily(Callers.id(principal), request.toContent());
        return ResponseEntity.created(URI.create(BASE + "/" + family.id()))
                .eTag(Versions.etag(family.version()))
                .body(FamilyResponse.from(family));
    }

    @Operation(
            summary = "Replace a family",
            description =
                    "Replaces everything about the family. The partners and children must exist.")
    @ApiResponse(
            responseCode = "200",
            description = "The changed family",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "New version of the family",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description = "No such family. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The family changed since it was read. The problem type is"
                            + " `urn:dulo:problem:concurrent-modification`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "428",
            description =
                    "If-Match is missing. The problem type is"
                            + " `urn:dulo:problem:precondition-required`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping(
            path = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FamilyResponse> replaceFamily(
            final Principal principal,
            @PathVariable final String id,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch,
            @RequestBody final FamilyRequest request) {
        return withETag(
                this.families.replaceFamily(
                        Callers.id(principal),
                        FamilyId.parse(id),
                        Versions.fromIfMatch(ifMatch),
                        request.toContent()));
    }

    @Operation(
            summary = "Delete a family",
            description =
                    "The family's people stay. Events of the family make the deletion fail with"
                            + " 409, which lists them. Send `confirmed=true` to delete it anyway:"
                            + " those events then belong to no family.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(
            responseCode = "404",
            description = "No such family. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "Other records refer to the family and the deletion isn't confirmed. The"
                            + " problem type is `urn:dulo:problem:has-links`. Its `links` member"
                            + " lists the references a confirmed deletion removes.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The family changed since it was read. The problem type is"
                            + " `urn:dulo:problem:concurrent-modification`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "428",
            description =
                    "If-Match is missing. The problem type is"
                            + " `urn:dulo:problem:precondition-required`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping(path = "/{id}")
    ResponseEntity<Void> deleteFamily(
            final Principal principal,
            @PathVariable final String id,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch,
            @Parameter(description = "Delete it even if other records refer to it.")
                    @RequestParam(defaultValue = "false")
                    final boolean confirmed) {
        this.families.deleteFamily(
                Callers.id(principal),
                FamilyId.parse(id),
                Versions.fromIfMatch(ifMatch),
                confirmed);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Get a family's history",
            description = "Every change to the family, oldest first, also after it was deleted.")
    @ApiResponse(responseCode = "200", description = "The changes")
    @ApiResponse(
            responseCode = "404",
            description =
                    "The family never existed. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/{id}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    List<ChangeEventResponse> familyHistory(
            final Principal principal, @PathVariable final String id) {
        return this.families.familyHistory(Callers.id(principal), FamilyId.parse(id)).stream()
                .map(ChangeEventResponse::from)
                .toList();
    }
}
