package com.dulo.adapter.rest.tree;

import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.error.TreeNotInitializedException;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.adapter.rest.version.Versions;
import com.dulo.core.tree.port.in.GetTreeUseCase;
import com.dulo.core.tree.port.in.TreeView;
import com.dulo.core.tree.port.in.UpdateTreeUseCase;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Tag(name = "Tree", description = "The family tree of this server")
@RestController
@RequestMapping("/api/v1/tree")
@SecurityRequirement(name = SecuritySchemes.OAUTH2)
class TreeController {

    private final GetTreeUseCase getTree;
    private final UpdateTreeUseCase updateTree;

    TreeController(final GetTreeUseCase getTree, final UpdateTreeUseCase updateTree) {
        this.getTree = getTree;
        this.updateTree = updateTree;
    }

    @Operation(
            summary = "Get the family tree",
            description =
                    "Returns the tree's name, description and the state of each feature. Every"
                            + " active user may read it.")
    @ApiResponse(
            responseCode = "200",
            description = "The tree",
            headers =
                    @Header(
                            name = "ETag",
                            description = "Version of the tree",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description =
                    "The initial setup is not done yet. The problem type is"
                            + " `urn:dulo:problem:tree-not-initialized`.",
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
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TreeResponse> getTree(final Principal principal) {
        final var view =
                this.getTree
                        .getTree(Callers.id(principal))
                        .orElseThrow(TreeNotInitializedException::new);
        return ResponseEntity.ok()
                .eTag(Versions.etag(view.tree().version()))
                .body(TreeResponse.from(view));
    }

    @Operation(
            summary = "Change the family tree",
            description =
                    "Changes the name, the description or the enabled features. Needs edit access"
                            + " to the administration feature. Left-out values stay as they are.")
    @ApiResponse(
            responseCode = "200",
            description = "The changed tree",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "New version of the tree",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "400",
            description =
                    "Invalid change, e.g. an unknown feature or an empty name. The problem type is"
                            + " `urn:dulo:problem:validation`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "403",
            description =
                    "The caller may not change the tree. The problem type is"
                            + " `urn:dulo:problem:forbidden`, or says why the account is not"
                            + " active.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The tree changed since it was read. The problem type is"
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
    @PatchMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TreeResponse> updateTree(
            final Principal principal,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch,
            @RequestBody final TreeChangeRequest request) {
        final TreeView view =
                this.updateTree.updateTree(
                        Callers.id(principal), Versions.fromIfMatch(ifMatch), request.toChange());
        return ResponseEntity.ok()
                .eTag(Versions.etag(view.tree().version()))
                .body(TreeResponse.from(view));
    }
}
