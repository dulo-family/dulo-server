package com.dulo.core.audit.recording;

import static org.assertj.core.api.Assertions.assertThat;

import com.dulo.core.testing.InMemoryChangeEvents;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.audit.Link;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ChangeSetTest {

    private static final Note NOTE = new Note("n1", "Hello", null, List.of("a"), 0);

    private final InMemoryChangeEvents events = new InMemoryChangeEvents();
    private final UserId actor = UserId.newId();

    @Test
    void diffKeepsTheFieldOrderAndSkipsEqualValues() {
        final Map<String, @Nullable Object> before = new LinkedHashMap<>();
        before.put("b", 1);
        before.put("a", "x");
        before.put("c", null);
        final Map<String, @Nullable Object> after = new HashMap<>();
        after.put("a", "y");
        after.put("b", 1L);
        after.put("d", List.of());

        assertThat(ChangeSet.diff(before, after))
                .containsExactly(
                        new FieldChange("a", "x", "y"), new FieldChange("d", null, List.of()));
    }

    @Test
    void createdHoldsEveryFieldWithAValue() {
        this.events.recorder().changeSet(this.actor).created(NoteFields.INSTANCE, NOTE);

        final ChangeEvent event = this.events.all().getFirst();
        assertThat(event.kind()).isEqualTo(ChangeKind.CREATED);
        assertThat(event.changes())
                .containsExactly(
                        new FieldChange("text", null, "Hello"),
                        new FieldChange("tags", null, List.of("a")));
        assertThat(event.sequence()).isEqualTo(1);
        assertThat(event.at()).isEqualTo(Instant.parse("2026-11-17T10:00:00Z"));
        assertThat(event.actor()).isEqualTo(this.actor);
        assertThat(event.entityType()).isEqualTo(EntityType.PLACE);
        assertThat(event.entityId()).isEqualTo("n1");
    }

    @Test
    void changedHoldsOnlyTheDifferencesAndNothingWhenNothingChanged() {
        final ChangeSet changes = this.events.recorder().changeSet(this.actor);
        final Note edited = new Note("n1", "Hello", "Sub", List.of(), 1);

        changes.changed(NoteFields.INSTANCE, NOTE, NOTE);
        changes.changed(NoteFields.INSTANCE, NOTE, edited);

        assertThat(this.events.all())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.kind()).isEqualTo(ChangeKind.CHANGED);
                    assertThat(event.entityVersion()).isEqualTo(1);
                    assertThat(event.changes())
                            .containsExactly(
                                    new FieldChange("subtitle", null, "Sub"),
                                    new FieldChange("tags", List.of("a"), List.of()));
                });
    }

    @Test
    void deletedHoldsEveryFieldAsItWasAndTheRemovedLinks() {
        final List<Link> links = List.of(new Link(EntityType.EVENT, "e1", "place"));
        final ChangeSet changes = this.events.recorder().changeSet(this.actor);

        changes.created(NoteFields.INSTANCE, NOTE);
        changes.deleted(NoteFields.INSTANCE, NOTE, links);

        final ChangeEvent event = this.events.all().getLast();
        assertThat(event.kind()).isEqualTo(ChangeKind.DELETED);
        assertThat(event.changes())
                .containsExactly(
                        new FieldChange("text", "Hello", null),
                        new FieldChange("tags", List.of("a"), null));
        assertThat(event.removedLinks()).isEqualTo(links);
        // One change set: one correlation.
        assertThat(this.events.all().getFirst().correlationId()).isEqualTo(event.correlationId());
    }

    @Test
    void everyChangeSetIsItsOwnAction() {
        this.events.recorder().changeSet(this.actor).created(NoteFields.INSTANCE, NOTE);
        this.events.recorder().changeSet(this.actor).created(NoteFields.INSTANCE, NOTE);

        assertThat(this.events.all().getFirst().correlationId())
                .isNotEqualTo(this.events.all().getLast().correlationId());
    }

    private record Note(
            String id, String text, @Nullable String subtitle, List<String> tags, long version) {}

    private static final class NoteFields implements RecordFields<Note> {

        static final NoteFields INSTANCE = new NoteFields();

        @Override
        public EntityType entityType() {
            return EntityType.PLACE;
        }

        @Override
        public String id(final Note note) {
            return note.id();
        }

        @Override
        public long version(final Note note) {
            return note.version();
        }

        @Override
        public Map<String, @Nullable Object> fields(final Note note) {
            final Map<String, @Nullable Object> fields = new LinkedHashMap<>();
            fields.put("text", note.text());
            fields.put("subtitle", note.subtitle());
            fields.put("tags", note.tags());
            return fields;
        }
    }
}
