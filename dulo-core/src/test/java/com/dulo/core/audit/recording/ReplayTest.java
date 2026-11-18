package com.dulo.core.audit.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.user.UserId;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class ReplayTest {

    private static final UUID ACTION = UUID.randomUUID();

    private static ChangeEvent event(final ChangeKind kind, final FieldChange... changes) {
        return new ChangeEvent(
                ChangeEventId.newId(),
                0,
                Instant.EPOCH,
                UserId.newId(),
                EntityType.PLACE,
                "p1",
                0,
                kind,
                ACTION,
                List.of(changes),
                List.of());
    }

    private final ChangeEvent created =
            event(
                    ChangeKind.CREATED,
                    new FieldChange("name", null, "Dorf"),
                    new FieldChange("type", null, "village"));
    private final ChangeEvent renamed =
            event(
                    ChangeKind.CHANGED,
                    new FieldChange("name", "Dorf", "Neudorf"),
                    new FieldChange("enclosedBy", null, "c1"));
    private final ChangeEvent moved =
            event(ChangeKind.CHANGED, new FieldChange("enclosedBy", "c1", null));
    private final ChangeEvent deleted =
            event(
                    ChangeKind.DELETED,
                    new FieldChange("name", "Neudorf", null),
                    new FieldChange("type", "village", null));
    private final ChangeEvent restored =
            event(
                    ChangeKind.RESTORED,
                    new FieldChange("name", null, "Dorf"),
                    new FieldChange("type", null, "village"));
    private final List<ChangeEvent> history =
            List.of(this.created, this.renamed, this.moved, this.deleted, this.restored);

    @Test
    void foldsTheChangesUpToAnEvent() {
        assertThat(Replay.stateAfter(this.history, this.created.id()))
                .isEqualTo(Map.of("name", "Dorf", "type", "village"));
        assertThat(Replay.stateAfter(this.history, this.renamed.id()))
                .isEqualTo(Map.of("name", "Neudorf", "type", "village", "enclosedBy", "c1"));
        assertThat(Replay.stateAfter(this.history, this.moved.id()))
                .isEqualTo(Map.of("name", "Neudorf", "type", "village"));
        // A deletion clears everything; a restore after it starts over.
        assertThat(Replay.stateAfter(this.history, this.restored.id()))
                .isEqualTo(Map.of("name", "Dorf", "type", "village"));
        assertThat(Replay.current(this.history))
                .isEqualTo(Map.of("name", "Dorf", "type", "village"));
    }

    @Test
    void aDeletionIsNoStateToRestore() {
        assertThatThrownBy(() -> Replay.stateAfter(this.history, this.deleted.id()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Replay.stateAfter(this.history, ChangeEventId.newId()))
                .isInstanceOf(NotFoundException.class);
        final List<ChangeEvent> deletedNow = this.history.subList(0, 4);
        assertThat(Replay.current(deletedNow)).isEmpty();
        assertThat(Replay.lastDeletion(deletedNow)).isEqualTo(this.deleted);
        assertThat(Replay.lastDeletion(this.history)).isNull();
        assertThat(Replay.current(List.of())).isEmpty();
    }

    @Test
    void findsTheEntryAnotherRecordLost() {
        final ChangeEvent unlinked =
                event(
                        ChangeKind.CHANGED,
                        new FieldChange(
                                "children",
                                List.of(
                                        Map.of("person", "a", "relation", "birth"),
                                        Map.of("person", "b", "relation", "adopted")),
                                List.of(Map.of("person", "a", "relation", "birth"))));

        assertThat(Replay.removedEntry(List.of(unlinked), ACTION, "children", "b"))
                .isEqualTo(Map.of("person", "b", "relation", "adopted"));
        assertThat(Replay.removedEntry(List.of(unlinked), UUID.randomUUID(), "children", "b"))
                .isNull();
        assertThat(Replay.removedEntry(List.of(unlinked), ACTION, "partners", "b")).isNull();
    }
}
