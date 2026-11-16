package com.dulo.core.tree.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryRoles;
import com.dulo.core.testing.InMemoryTrees;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.core.tree.port.in.TreeView;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.feature.FeatureState;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

class TreeServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryTrees trees = new InMemoryTrees(null);
    private final FeatureOverrides overrides =
            new FeatureOverrides(Map.of(Feature.MAP_VIEW, false));
    private final TreeService service =
            new TreeService(
                    this.trees,
                    () -> this.overrides,
                    new AccessPolicy(
                            this.users,
                            new InMemoryRoles(DefaultRoles.all()),
                            this.trees,
                            () -> this.overrides),
                    new DirectUnitOfWork());

    @Test
    void emptyBeforeSetup() {
        assertThat(this.service.getTree(this.user(UserStatus.ACTIVE))).isEmpty();
    }

    @Test
    void appliesConfigurationOverrides() {
        final TreeSettings tree = TreeSettings.create("Smiths", null, Set.of(Feature.MAP_VIEW));
        this.trees.save(tree);

        final TreeView view = this.service.getTree(this.user(UserStatus.ACTIVE)).orElseThrow();

        assertThat(view.tree()).isEqualTo(tree);
        assertThat(view.features()).contains(new FeatureState(Feature.MAP_VIEW, false, true));
    }

    @Test
    void anyActiveUserMayReadTheTree() {
        this.trees.save(TreeSettings.create("Smiths", null, Set.of()));

        // No roles at all, but active.
        assertThat(this.service.getTree(this.user(UserStatus.ACTIVE))).isPresent();
        assertThatThrownBy(() -> this.service.getTree(this.user(UserStatus.PENDING)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private UserId user(final UserStatus status) {
        return this.users.save(Users.user(status)).id();
    }
}
