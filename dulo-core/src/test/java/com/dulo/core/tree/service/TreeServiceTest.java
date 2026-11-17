package com.dulo.core.tree.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryChangeEvents;
import com.dulo.core.testing.InMemoryRoles;
import com.dulo.core.testing.InMemoryTrees;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.core.tree.port.in.TreeView;
import com.dulo.core.tree.port.in.UpdateTreeUseCase;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.feature.FeatureState;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class TreeServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryTrees trees = new InMemoryTrees(null);
    private final FeatureOverrides overrides =
            new FeatureOverrides(Map.of(Feature.MAP_VIEW, false));
    private final InMemoryChangeEvents events = new InMemoryChangeEvents();
    private final TreeService service =
            new TreeService(
                    this.trees,
                    () -> this.overrides,
                    new AccessPolicy(
                            this.users,
                            new InMemoryRoles(DefaultRoles.all()),
                            this.trees,
                            () -> this.overrides),
                    this.events.recorder(),
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

    @Test
    void administratorsChangeTheTree() {
        final TreeSettings tree =
                this.trees.save(TreeSettings.create("The Smiths", "Since 1820", Set.of()));
        final UserId admin =
                this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.ADMIN)).id();

        final TreeView renamed =
                this.service.updateTree(
                        admin,
                        tree.version(),
                        new UpdateTreeUseCase.TreeChange(
                                "Smith family", null, Set.of(Feature.TIMELINE)));

        assertThat(renamed.tree().name()).isEqualTo("Smith family");
        assertThat(renamed.tree().description()).isEqualTo("Since 1820");
        assertThat(renamed.tree().enabledFeatures()).contains(Feature.TIMELINE);
        final TreeView cleared =
                this.service.updateTree(
                        admin,
                        renamed.tree().version(),
                        new UpdateTreeUseCase.TreeChange(null, "", null));
        assertThat(cleared.tree().name()).isEqualTo("Smith family");
        assertThat(cleared.tree().description()).isNull();
        assertThat(cleared.tree().enabledFeatures()).contains(Feature.TIMELINE);
        assertThat(this.events.kinds(EntityType.TREE))
                .containsExactly(ChangeKind.CHANGED, ChangeKind.CHANGED);
        assertThat(this.events.all().getFirst().changes())
                .containsExactly(
                        new FieldChange("name", "The Smiths", "Smith family"),
                        new FieldChange("enabledFeatures", List.of(), List.of("timeline")));
        assertThat(this.events.all().getLast().changes())
                .containsExactly(new FieldChange("description", "Since 1820", null));
    }

    @Test
    void treeChangesNeedAdministrationAndTheCurrentVersion() {
        final TreeSettings tree =
                this.trees.save(TreeSettings.create("The Smiths", null, Set.of()));
        final UserId editor =
                this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.EDITOR)).id();
        final UserId admin =
                this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.ADMIN)).id();
        final UpdateTreeUseCase.TreeChange change =
                new UpdateTreeUseCase.TreeChange("Renamed", null, null);

        assertThatThrownBy(() -> this.service.updateTree(editor, tree.version(), change))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> this.service.updateTree(admin, tree.version() + 1, change))
                .isInstanceOf(ConcurrentModificationException.class);
        this.trees.clear();
        assertThatThrownBy(() -> this.service.updateTree(admin, 0, change))
                .isInstanceOf(NotFoundException.class);
    }
}
