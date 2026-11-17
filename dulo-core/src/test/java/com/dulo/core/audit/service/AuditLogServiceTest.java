package com.dulo.core.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.access.service.RoleFields;
import com.dulo.core.audit.port.in.QueryAuditLogUseCase.AuditFilter;
import com.dulo.core.audit.recording.ChangeSet;
import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryChangeEvents;
import com.dulo.core.testing.InMemoryRoles;
import com.dulo.core.testing.InMemoryTrees;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.core.tree.service.TreeFields;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.access.Role;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

class AuditLogServiceTest {

    private static final Instant RECORDED = Instant.parse("2026-11-17T10:00:00Z");

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryTrees trees =
            new InMemoryTrees(TreeSettings.create("The Smiths", null, Set.of(Feature.AUDIT_LOG)));
    private final InMemoryChangeEvents events = new InMemoryChangeEvents();
    private final AuditLogService service =
            new AuditLogService(
                    this.events,
                    new AccessPolicy(
                            this.users,
                            new InMemoryRoles(DefaultRoles.all()),
                            this.trees,
                            FeatureOverrides::none),
                    new DirectUnitOfWork());
    private final UserId admin =
            this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.ADMIN)).id();
    private final UserId editor =
            this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.EDITOR)).id();

    /** Records a role change by the admin and a tree change by the editor, 5 events in all. */
    private void recordSomeChanges() {
        final ChangeSet byAdmin = this.events.recorder().changeSet(this.admin);
        for (final Role role : DefaultRoles.all()) {
            byAdmin.created(RoleFields.INSTANCE, role);
        }
        final TreeSettings tree = this.trees.find().orElseThrow();
        this.events
                .recorder()
                .changeSet(this.editor)
                .changed(
                        TreeFields.INSTANCE,
                        tree,
                        new TreeSettings(tree.id(), "Renamed", null, Set.of(), 1));
    }

    @Test
    void pagesNewestFirst() {
        this.recordSomeChanges();

        final CursorPage<ChangeEvent> first =
                this.service.changes(this.admin, AuditFilter.all(), null, 2);
        final CursorPage<ChangeEvent> second =
                this.service.changes(this.admin, AuditFilter.all(), first.next(), 2);
        final CursorPage<ChangeEvent> last =
                this.service.changes(this.admin, AuditFilter.all(), second.next(), 2);

        assertThat(first.items()).extracting(ChangeEvent::sequence).containsExactly(5L, 4L);
        assertThat(second.items()).extracting(ChangeEvent::sequence).containsExactly(3L, 2L);
        assertThat(last.items()).extracting(ChangeEvent::sequence).containsExactly(1L);
        assertThat(last.next()).isNull();
        // A page that is exactly full has no next page either.
        assertThat(this.service.changes(this.admin, AuditFilter.all(), null, 5).next()).isNull();
    }

    @Test
    void filtersByTypeIdActorAndTime() {
        this.recordSomeChanges();

        assertThat(this.page(new AuditFilter(EntityType.ROLE, "editor", null, null, null)))
                .singleElement()
                .satisfies(event -> assertThat(event.entityId()).isEqualTo("editor"));
        assertThat(this.page(new AuditFilter(null, null, this.editor, null, null)))
                .singleElement()
                .satisfies(event -> assertThat(event.entityType()).isEqualTo(EntityType.TREE));
        assertThat(this.page(new AuditFilter(null, null, null, RECORDED, null))).hasSize(5);
        assertThat(this.page(new AuditFilter(null, null, null, null, RECORDED))).isEmpty();
        assertThatThrownBy(() -> new AuditFilter(null, null, null, RECORDED, RECORDED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAdministratorsSeeAdministrativeChanges() {
        this.recordSomeChanges();

        // The editor may view the audit log, but not the administration.
        assertThat(this.service.changes(this.editor, AuditFilter.all(), null, 50).items())
                .isEmpty();
        assertThatThrownBy(() ->
                        this.service.changes(
                                this.editor,
                                new AuditFilter(EntityType.TREE, null, null, null, null),
                                null,
                                50))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(
                        this.service.changes(
                                this.editor,
                                new AuditFilter(EntityType.PERSON, null, null, null, null),
                                null,
                                50))
                .extracting(CursorPage::items)
                .isEqualTo(List.of());
    }

    @Test
    void needsTheAuditLogFeatureAValidCursorAndLimit() {
        final UserId viewer =
                this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.VIEWER)).id();

        assertThatThrownBy(() -> this.service.changes(viewer, AuditFilter.all(), null, 50))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> this.service.changes(this.admin, AuditFilter.all(), "x!", 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> this.service.changes(this.admin, AuditFilter.all(), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<ChangeEvent> page(final AuditFilter filter) {
        return this.service.changes(this.admin, filter, null, 50).items();
    }
}
