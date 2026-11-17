package com.dulo.adapter.rest.audit;

import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.audit.Link;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@Schema(name = "ChangeEvent", description = "One change to one record.")
public record ChangeEventResponse(
        String id,
        @Schema(description = "When the change happened.") Instant at,
        @Schema(description = "The id of the user who made the change.") String actor,
        @Schema(allowableValues = {"event", "family", "person", "place", "role", "tree", "user"})
                String entityType,
        @Schema(description = "The record's id; for roles, the role key.") String entityId,
        @Schema(
                        description =
                                "The record's version after the change; for a deletion, its last"
                                        + " version.")
                long entityVersion,
        @Schema(allowableValues = {"created", "changed", "deleted", "restored"}) String kind,
        @Schema(description = "Shared by the changes of one action.") String correlationId,
        @Schema(
                        description =
                                "The fields the change affected. A creation lists every field with"
                                        + " a value, a deletion every field as it was.")
                List<FieldChangeResponse> changes,
        @Schema(description = "For a deletion: the references to the record it removed.")
                List<LinkResponse> removedLinks) {

    public static ChangeEventResponse from(final ChangeEvent event) {
        return new ChangeEventResponse(
                event.id().toString(),
                event.at(),
                event.actor().toString(),
                event.entityType().key(),
                event.entityId(),
                event.entityVersion(),
                event.kind().key(),
                event.correlationId().toString(),
                event.changes().stream().map(FieldChangeResponse::from).toList(),
                event.removedLinks().stream().map(LinkResponse::from).toList());
    }

    @Schema(name = "FieldChange")
    public record FieldChangeResponse(
            String field,
            @Schema(
                            description =
                                    "The value before; null when the field had none or the"
                                            + " record didn't exist.",
                            types = {"string", "number", "boolean", "array", "object"})
                    @Nullable Object before,
            @Schema(
                            description =
                                    "The value after; null when the field has none or the record"
                                            + " was deleted.",
                            types = {"string", "number", "boolean", "array", "object"})
                    @Nullable Object after) {

        static FieldChangeResponse from(final FieldChange change) {
            return new FieldChangeResponse(change.field(), change.before(), change.after());
        }
    }

    @Schema(name = "Link", description = "A reference from one record to another.")
    public record LinkResponse(
            @Schema(description = "The type of the record that holds the reference.")
                    String entityType,
            @Schema(description = "The id of the record that holds the reference.") String entityId,
            @Schema(description = "The field of that record that holds the reference.")
                    String field) {

        public static LinkResponse from(final Link link) {
            return new LinkResponse(link.entityType().key(), link.entityId(), link.field());
        }
    }
}
