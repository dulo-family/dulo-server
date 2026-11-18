package com.dulo.adapter.rest.event;

import com.dulo.adapter.rest.audit.ChangeEventResponse;
import com.dulo.adapter.rest.audit.RestoreRequest;
import com.dulo.adapter.rest.caller.Callers;
import com.dulo.adapter.rest.openapi.SecuritySchemes;
import com.dulo.adapter.rest.paging.PageResponse;
import com.dulo.adapter.rest.version.Versions;
import com.dulo.core.event.port.in.ManageEventsUseCase;
import com.dulo.core.event.port.in.ManageEventsUseCase.EventFilter;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.place.PlaceId;

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
        name = "Events",
        description =
                "Births, marriages, burials and everything else that happened. Needs the events"
                        + " feature: view to read, create, edit and delete to change.")
@RestController
@RequestMapping("/api/v1/events")
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
class EventsController {

    private static final String BASE = "/api/v1/events";

    private final ManageEventsUseCase events;

    EventsController(final ManageEventsUseCase events) {
        this.events = events;
    }

    private static ResponseEntity<EventResponse> withETag(final Event event) {
        return ResponseEntity.ok()
                .eTag(Versions.etag(event.version()))
                .body(EventResponse.from(event));
    }

    @Operation(
            summary = "List events",
            description = "Sorted by date, the start of a range; undated events last. In pages.")
    @ApiResponse(responseCode = "200", description = "One page of events")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    PageResponse<EventResponse> events(
            final Principal principal,
            @Parameter(description = "Only events this person took part in.")
                    @RequestParam(required = false)
                    final @Nullable String person,
            @Parameter(description = "Only events at this place.") @RequestParam(required = false)
                    final @Nullable String place,
            @Parameter(description = "Only events of this family.") @RequestParam(required = false)
                    final @Nullable String family,
            @Parameter(description = "Only events of this type: " + EventTypes.DESCRIPTION)
                    @RequestParam(required = false)
                    final @Nullable String type,
            @Parameter(description = PageResponse.AFTER_DESCRIPTION) @RequestParam(required = false)
                    final @Nullable String after,
            @Parameter(description = PageResponse.LIMIT_DESCRIPTION)
                    @RequestParam(defaultValue = PageResponse.DEFAULT_LIMIT)
                    final int limit) {
        final EventFilter filter =
                new EventFilter(
                        person == null ? null : PersonId.parse(person),
                        place == null ? null : PlaceId.parse(place),
                        family == null ? null : FamilyId.parse(family),
                        type == null ? null : EventTypes.parse(type));
        return PageResponse.from(
                this.events.events(Callers.id(principal), filter, after, limit),
                EventResponse::from);
    }

    @Operation(summary = "Get an event")
    @ApiResponse(
            responseCode = "200",
            description = "The event",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "Version of the event",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description = "No such event. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EventResponse> event(final Principal principal, @PathVariable final String id) {
        return withETag(this.events.event(Callers.id(principal), EventId.parse(id)));
    }

    @Operation(
            summary = "Create an event",
            description = "The participants, the place and the family must exist.")
    @ApiResponse(
            responseCode = "201",
            description = "The new event",
            headers = {
                @Header(
                        name = HttpHeaders.LOCATION,
                        description = "Path of the event",
                        schema = @Schema(type = "string")),
                @Header(
                        name = HttpHeaders.ETAG,
                        description = "Version of the event",
                        schema = @Schema(type = "string"))
            })
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EventResponse> createEvent(
            final Principal principal, @RequestBody final EventRequest request) {
        final Event event = this.events.createEvent(Callers.id(principal), request.toContent());
        return ResponseEntity.created(URI.create(BASE + "/" + event.id()))
                .eTag(Versions.etag(event.version()))
                .body(EventResponse.from(event));
    }

    @Operation(
            summary = "Replace an event",
            description =
                    "Replaces everything about the event: values left out are removed. The"
                            + " participants, the place and the family must exist.")
    @ApiResponse(
            responseCode = "200",
            description = "The changed event",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "New version of the event",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description = "No such event. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The event changed since it was read. The problem type is"
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
    ResponseEntity<EventResponse> replaceEvent(
            final Principal principal,
            @PathVariable final String id,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch,
            @RequestBody final EventRequest request) {
        return withETag(
                this.events.replaceEvent(
                        Callers.id(principal),
                        EventId.parse(id),
                        Versions.fromIfMatch(ifMatch),
                        request.toContent()));
    }

    @Operation(
            summary = "Delete an event",
            description = "Nothing refers to events, so a deletion needs no confirmation.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(
            responseCode = "404",
            description = "No such event. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The event changed since it was read. The problem type is"
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
    ResponseEntity<Void> deleteEvent(
            final Principal principal,
            @PathVariable final String id,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description = Versions.IF_MATCH_DESCRIPTION,
                            required = true)
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch) {
        this.events.deleteEvent(
                Callers.id(principal), EventId.parse(id), Versions.fromIfMatch(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Restore an event",
            description =
                    "Brings the event back to the state it had right after a change from its"
                            + " history, as a new change: also a deleted event."
                            + " Links that can't come back, such as to records that are gone,"
                            + " make it fail with 409, which lists them; send `confirmed=true` to"
                            + " restore without them. Needs edit access, or create access for a"
                            + " deleted event.")
    @ApiResponse(
            responseCode = "200",
            description = "The restored event",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "New version of the event",
                            schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "404",
            description =
                    "The event never existed, or the change isn't its. The problem type is"
                            + " `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "Some links can't come back and the restore isn't confirmed. The problem type"
                            + " is `urn:dulo:problem:restore-conflict`. Its `links` member lists"
                            + " them: the other record and the field.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "412",
            description =
                    "The event changed since it was read. The problem type is"
                            + " `urn:dulo:problem:concurrent-modification`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "428",
            description =
                    "The event exists and If-Match is missing. The problem type is"
                            + " `urn:dulo:problem:precondition-required`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(
            path = "/{id}/restore",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EventResponse> restoreEvent(
            final Principal principal,
            @PathVariable final String id,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = HttpHeaders.IF_MATCH,
                            description =
                                    "The version you saw, from the ETag. Required while the"
                                            + " event exists; leave it out for a deleted one.")
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    final @Nullable String ifMatch,
            @Parameter(description = "Restore even if some links can't come back.")
                    @RequestParam(defaultValue = "false")
                    final boolean confirmed,
            @RequestBody final RestoreRequest request) {
        return withETag(
                this.events.restoreEvent(
                        Callers.id(principal),
                        EventId.parse(id),
                        request.toEventId(),
                        ifMatch == null ? null : Versions.fromIfMatch(ifMatch),
                        confirmed));
    }

    @Operation(
            summary = "Get an event's history",
            description = "Every change to the event, oldest first, also after it was deleted.")
    @ApiResponse(responseCode = "200", description = "The changes")
    @ApiResponse(
            responseCode = "404",
            description =
                    "The event never existed. The problem type is `urn:dulo:problem:not-found`.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/{id}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    List<ChangeEventResponse> eventHistory(
            final Principal principal, @PathVariable final String id) {
        return this.events.eventHistory(Callers.id(principal), EventId.parse(id)).stream()
                .map(ChangeEventResponse::from)
                .toList();
    }
}
