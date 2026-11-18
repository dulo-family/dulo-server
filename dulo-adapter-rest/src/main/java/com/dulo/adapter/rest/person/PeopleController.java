package com.dulo.adapter.rest.person;

import com.dulo.adapter.rest.audit.ChangeEventResponse;
import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.adapter.rest.paging.PageResponse;
import com.dulo.adapter.rest.version.Versions;
import com.dulo.core.person.port.in.ManagePeopleUseCase;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonSort;

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
        name = "People",
        description =
                "The people of the tree and their names. Needs the people feature: view to read,"
                        + " create, edit and delete to change.")
@RestController
@RequestMapping("/api/v1/people")
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
class PeopleController {

    private static final String BASE = "/api/v1/people";

    private final ManagePeopleUseCase people;

    PeopleController(final ManagePeopleUseCase people) {
        this.people = people;
    }

    private static ResponseEntity<PersonResponse> withETag(final Person person) {
        return ResponseEntity.ok()
                .eTag(Versions.etag(person.version()))
                .body(PersonResponse.from(person));
    }

    @Operation(
            summary = "List people",
            description =
                    "Sorted by the primary name, by default given name, then surname; people"
                            + " without a name come first. In pages.")
    @ApiResponse(responseCode = "200", description = "One page of people")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    PageResponse<PersonResponse> people(
            final Principal principal,
            @Parameter(
                            description =
                                    "Only people with a name that contains this text, ignoring"
                                            + " case. All names and all their parts count.")
                    @RequestParam(name = "q", required = false)
                    final @Nullable String search,
            @Parameter(
                            description =
                                    "`given`: given name, then surname. `surname`: surname, then"
                                            + " given name. A cursor only reads on in the order it"
                                            + " was made for.",
                            schema = @Schema(allowableValues = {"given", "surname"}))
                    @RequestParam(defaultValue = "given")
                    final String sort,
            @Parameter(description = PageResponse.AFTER_DESCRIPTION) @RequestParam(required = false)
                    final @Nullable String after,
            @Parameter(description = PageResponse.LIMIT_DESCRIPTION)
                    @RequestParam(defaultValue = PageResponse.DEFAULT_LIMIT)
                    final int limit) {
        return PageResponse.from(
                this.people.people(
                        Callers.id(principal),
                        search,
                        PersonSort.fromKey(sort)
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "Unknown sort '%s'".formatted(sort))),
                        after,
                        limit),
                PersonResponse::from);
    }

    @Operation(summary = "Get a person")
    @ApiResponse(
            responseCode = "200",
            description = "The person",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "Version of the person",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description = "No such person. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PersonResponse> person(
            final Principal principal, @PathVariable final String id) {
        return withETag(this.people.person(Callers.id(principal), PersonId.parse(id)));
    }

    @Operation(summary = "Create a person")
    @ApiResponse(
            responseCode = "201",
            description = "The new person",
            headers = {
                @Header(
                        name = HttpHeaders.LOCATION,
                        description = "Path of the person",
                        schema = @Schema(type = "string")),
                @Header(
                        name = HttpHeaders.ETAG,
                        description = "Version of the person",
                        schema = @Schema(type = "string"))
            })
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PersonResponse> createPerson(
            final Principal principal, @RequestBody final PersonRequest request) {
        final Person person = this.people.createPerson(Callers.id(principal), request.toContent());
        return ResponseEntity.created(URI.create(BASE + "/" + person.id()))
                .eTag(Versions.etag(person.version()))
                .body(PersonResponse.from(person));
    }

    @Operation(
            summary = "Replace a person",
            description =
                    "Replaces everything about the person, names included: values left out are"
                            + " removed.")
    @ApiResponse(
            responseCode = "200",
            description = "The changed person",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "New version of the person",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description = "No such person. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The person changed since it was read. The problem type is"
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
    ResponseEntity<PersonResponse> replacePerson(
            final Principal principal,
            @PathVariable final String id,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch,
            @RequestBody final PersonRequest request) {
        return withETag(
                this.people.replacePerson(
                        Callers.id(principal),
                        PersonId.parse(id),
                        Versions.fromIfMatch(ifMatch),
                        request.toContent()));
    }

    @Operation(
            summary = "Delete a person",
            description =
                    "Records that refer to the person make the deletion fail with 409, which lists"
                            + " them. Send `confirmed=true` to delete them anyway, removing those"
                            + " references.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(
            responseCode = "404",
            description = "No such person. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "Other records refer to the person and the deletion isn't confirmed. The"
                            + " problem type is `urn:dulo:problem:has-links`. Its `links` member"
                            + " lists the references a confirmed deletion removes.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The person changed since it was read. The problem type is"
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
    ResponseEntity<Void> deletePerson(
            final Principal principal,
            @PathVariable final String id,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch,
            @Parameter(description = "Delete them even if other records refer to them.")
                    @RequestParam(defaultValue = "false")
                    final boolean confirmed) {
        this.people.deletePerson(
                Callers.id(principal),
                PersonId.parse(id),
                Versions.fromIfMatch(ifMatch),
                confirmed);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Get a person's history",
            description = "Every change to the person, oldest first, also after they were deleted.")
    @ApiResponse(responseCode = "200", description = "The changes")
    @ApiResponse(
            responseCode = "404",
            description =
                    "The person never existed. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/{id}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    List<ChangeEventResponse> personHistory(
            final Principal principal, @PathVariable final String id) {
        return this.people.personHistory(Callers.id(principal), PersonId.parse(id)).stream()
                .map(ChangeEventResponse::from)
                .toList();
    }
}
