package com.dulo.core.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.access.port.in.ManageUsersUseCase.UserChange;
import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryChangeEvents;
import com.dulo.core.testing.InMemoryRoles;
import com.dulo.core.testing.InMemoryTrees;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.access.Permissions;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class UserAdministrationServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryRoles roles = new InMemoryRoles(DefaultRoles.all());
    private final InMemoryChangeEvents events = new InMemoryChangeEvents();
    private final UserAdministrationService service =
            new UserAdministrationService(
                    this.users,
                    this.roles,
                    new AccessPolicy(
                            this.users,
                            this.roles,
                            new InMemoryTrees(null),
                            FeatureOverrides::none),
                    this.events.recorder(),
                    new DirectUnitOfWork());
    private final UserId admin =
            this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.ADMIN)).id();

    @Test
    void administratorsListAndReadUsers() {
        final User pending = this.users.save(Users.user(UserStatus.PENDING));

        assertThat(this.service.users(this.admin))
                .extracting(User::id)
                .containsExactly(this.admin, pending.id());
        assertThat(this.service.user(this.admin, pending.id())).isEqualTo(pending);
        assertThatThrownBy(() -> this.service.user(this.admin, UserId.newId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void administratorsApproveUsersAndAssignRoles() {
        final User pending = this.users.save(Users.user(UserStatus.PENDING));

        final User approved =
                this.service.updateUser(
                        this.admin,
                        pending.id(),
                        pending.version(),
                        new UserChange(UserStatus.ACTIVE, Set.of(DefaultRoles.EDITOR)));

        assertThat(approved.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(approved.manualRoles()).containsExactly(DefaultRoles.EDITOR);
        assertThat(approved.version()).isEqualTo(pending.version() + 1);
        // Null leaves a value as it is.
        final User unchanged =
                this.service.updateUser(
                        this.admin, pending.id(), approved.version(), new UserChange(null, null));
        assertThat(unchanged.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(unchanged.manualRoles()).containsExactly(DefaultRoles.EDITOR);
        // A change that changes nothing leaves no event.
        assertThat(this.events.all())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.kind()).isEqualTo(ChangeKind.CHANGED);
                    assertThat(event.entityId()).isEqualTo(pending.id().toString());
                    assertThat(event.changes())
                            .containsExactly(
                                    new FieldChange("status", "pending", "active"),
                                    new FieldChange("manualRoles", List.of(), List.of("editor")));
                });
    }

    @Test
    void rejectsInvalidChanges() {
        final User other = this.users.save(Users.user(UserStatus.ACTIVE));
        final User root = this.users.save(Users.user(UserStatus.ACTIVE).asRoot());
        final long adminVersion = this.users.findById(this.admin).orElseThrow().version();

        assertThatThrownBy(() ->
                        this.service.updateUser(
                                this.admin,
                                other.id(),
                                other.version(),
                                new UserChange(null, Set.of(new RoleKey("ghosts")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'ghosts'");
        assertThatThrownBy(() ->
                        this.service.updateUser(
                                this.admin,
                                this.admin,
                                adminVersion,
                                new UserChange(UserStatus.DISABLED, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own status");
        assertThatThrownBy(() ->
                        this.service.updateUser(
                                this.admin,
                                root.id(),
                                root.version(),
                                new UserChange(UserStatus.DISABLED, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("root");
        assertThatThrownBy(() ->
                        this.service.updateUser(
                                this.admin,
                                other.id(),
                                other.version() + 1,
                                new UserChange(UserStatus.DISABLED, null)))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() ->
                        this.service.updateUser(
                                this.admin, UserId.newId(), 0, new UserChange(null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyAdministratorsManageUsers() {
        final User viewer = this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.VIEWER));

        assertThatThrownBy(() -> this.service.users(viewer.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> this.service.user(viewer.id(), this.admin))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.service.updateUser(
                                viewer.id(),
                                viewer.id(),
                                viewer.version(),
                                new UserChange(null, Set.of(DefaultRoles.ADMIN))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void partialAdministratorsCantGrantMoreThanTheyHave() {
        // May approve users and assign roles, and see people, but not more.
        final Role moderators =
                this.roles.save(
                        new Role(
                                new RoleKey("moderators"),
                                "Moderators",
                                false,
                                new Permissions(
                                        Map.of(
                                                Feature.ADMINISTRATION,
                                                Set.of(Access.VIEW, Access.EDIT),
                                                Feature.PEOPLE,
                                                Set.of(Access.VIEW))),
                                0));
        final Role readers =
                this.roles.save(
                        new Role(
                                new RoleKey("readers"),
                                "Readers",
                                false,
                                new Permissions(Map.of(Feature.PEOPLE, Set.of(Access.VIEW))),
                                0));
        final User moderator = this.users.save(Users.user(UserStatus.ACTIVE, moderators.key()));
        final User pending = this.users.save(Users.user(UserStatus.PENDING));
        final User admin = this.users.findById(this.admin).orElseThrow();

        // Approving someone with no roles, and giving them a role within one's own, works.
        final User approved =
                this.service.updateUser(
                        moderator.id(),
                        pending.id(),
                        pending.version(),
                        new UserChange(UserStatus.ACTIVE, Set.of(readers.key())));
        assertThat(approved.manualRoles()).containsExactly(readers.key());

        // Giving oneself or others more than one has doesn't.
        assertThatThrownBy(() ->
                        this.service.updateUser(
                                moderator.id(),
                                moderator.id(),
                                moderator.version(),
                                new UserChange(null, Set.of(moderators.key(), DefaultRoles.ADMIN))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("'admin'");
        assertThatThrownBy(() ->
                        this.service.updateUser(
                                moderator.id(),
                                approved.id(),
                                approved.version(),
                                new UserChange(null, Set.of(DefaultRoles.VIEWER))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("'viewer'");
        // Nor can they touch someone who may do more than they may.
        assertThatThrownBy(() ->
                        this.service.updateUser(
                                moderator.id(),
                                admin.id(),
                                admin.version(),
                                new UserChange(UserStatus.DISABLED, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("may do more than you");
        // Keeping a role the user already has is fine; only added roles count.
        assertThat(
                        this.service
                                .updateUser(
                                        moderator.id(),
                                        moderator.id(),
                                        moderator.version(),
                                        new UserChange(
                                                null, Set.of(moderators.key(), readers.key())))
                                .manualRoles())
                .containsExactlyInAnyOrder(moderators.key(), readers.key());
    }
}
