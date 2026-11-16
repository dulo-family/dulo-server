package com.dulo.core.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.access.port.in.CurrentUserView;
import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryRoles;
import com.dulo.core.testing.InMemoryTrees;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.access.Permissions;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

class CurrentUserServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryTrees trees =
            new InMemoryTrees(
                    TreeSettings.create("Smiths", null, Set.of(Feature.PEOPLE, Feature.MAP_VIEW)));
    private final CurrentUserService service =
            new CurrentUserService(
                    this.users,
                    new AccessPolicy(
                            this.users,
                            new InMemoryRoles(DefaultRoles.all()),
                            this.trees,
                            () -> new FeatureOverrides(Map.of(Feature.MAP_VIEW, false))),
                    new DirectUnitOfWork());

    @Test
    void permissionsCoverOnlyEnabledFeatures() {
        final User viewer = this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.VIEWER));

        final CurrentUserView view = this.service.currentUser(viewer.id());

        assertThat(view.user()).isEqualTo(viewer);
        assertThat(view.permissions().grants())
                .containsOnlyKeys(Feature.PEOPLE)
                .containsEntry(Feature.PEOPLE, Set.of(Access.VIEW));
    }

    @Test
    void rootMayDoEverythingOnEnabledFeatures() {
        final User root = this.users.save(Users.user(UserStatus.ACTIVE).asRoot());

        assertThat(this.service.currentUser(root.id()).permissions().grants())
                .containsOnlyKeys(Feature.PEOPLE, Feature.ADMINISTRATION)
                .containsEntry(Feature.ADMINISTRATION, Set.of(Access.values()));
    }

    @Test
    void pendingUsersSeeThemselvesButMayDoNothing() {
        final User pending = this.users.save(Users.user(UserStatus.PENDING, DefaultRoles.ADMIN));

        final CurrentUserView view = this.service.currentUser(pending.id());

        assertThat(view.user().status()).isEqualTo(UserStatus.PENDING);
        assertThat(view.permissions()).isEqualTo(Permissions.none());
    }

    @Test
    void unknownUsersAreDenied() {
        assertThatThrownBy(() -> this.service.currentUser(UserId.newId()))
                .isInstanceOfSatisfying(
                        AccessDeniedException.class,
                        e ->
                                assertThat(e.reason())
                                        .isEqualTo(AccessDeniedException.Reason.UNKNOWN_USER));
    }
}
