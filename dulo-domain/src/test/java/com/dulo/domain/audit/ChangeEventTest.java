package com.dulo.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.domain.user.UserId;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

class ChangeEventTest {

    private static ChangeEvent event(final List<FieldChange> changes) {
        return new ChangeEvent(
                ChangeEventId.newId(),
                0,
                Instant.EPOCH,
                UserId.newId(),
                EntityType.ROLE,
                "editor",
                1,
                ChangeKind.CHANGED,
                UUID.randomUUID(),
                changes,
                List.of());
    }

    @Test
    void getsItsSequenceWhenStored() {
        final ChangeEvent event = event(List.of(new FieldChange("name", "Editor", "Editors")));

        final ChangeEvent stored = event.withSequence(7);

        assertThat(stored.sequence()).isEqualTo(7);
        assertThat(stored).isEqualTo(event.withSequence(7));
        assertThat(stored.id()).isEqualTo(event.id());
    }

    @Test
    void aFieldChangesOnlyOnce() {
        assertThatThrownBy(() ->
                        event(
                                List.of(
                                        new FieldChange("name", "a", "b"),
                                        new FieldChange("name", "b", "c"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typesAndKindsHaveStableKeys() {
        assertThat(EntityType.fromKey("Person")).contains(EntityType.PERSON);
        assertThat(EntityType.fromKey("nope")).isEmpty();
        assertThat(EntityType.ROLE.administrative()).isTrue();
        assertThat(EntityType.PLACE.administrative()).isFalse();
        assertThat(ChangeKind.RESTORED.key()).isEqualTo("restored");
        final ChangeEventId id = ChangeEventId.newId();
        assertThat(ChangeEventId.parse(id.toString())).isEqualTo(id);
    }
}
