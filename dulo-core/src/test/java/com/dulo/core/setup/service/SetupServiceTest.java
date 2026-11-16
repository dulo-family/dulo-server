package com.dulo.core.setup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.identity.port.out.IdentityProvidersPort;
import com.dulo.core.setup.port.in.InvalidSetupTokenException;
import com.dulo.core.setup.port.in.SetupCommand;
import com.dulo.core.setup.port.in.SetupCompletedException;
import com.dulo.core.setup.port.in.SetupDefaults;
import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryRoles;
import com.dulo.core.testing.InMemoryTrees;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.core.tree.port.in.TreeView;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.access.Permissions;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.feature.FeatureState;
import com.dulo.domain.identity.IdentityProvider;
import com.dulo.domain.identity.IdentityProviderType;
import com.dulo.domain.identity.RegistrationMode;
import com.dulo.domain.identity.RoleMapping;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class SetupServiceTest {

    private static final String TOKEN = "setup-token";
    private static final RoleKey READERS = new RoleKey("readers");
    private static final IdentityProvider POCKET_ID =
            new IdentityProvider("pocketid", "Pocket ID", IdentityProviderType.OIDC);

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryRoles roles = new InMemoryRoles(List.of());
    private final InMemoryTrees trees = new InMemoryTrees(null);
    private RoleMapping mapping =
            new RoleMapping(Map.of("admins", Set.of(DefaultRoles.ADMIN)), null);
    private final SetupService service =
            new SetupService(
                    this.users,
                    this.roles,
                    this.trees,
                    new IdentityProvidersPort() {
                        @Override
                        public List<IdentityProvider> providers() {
                            return List.of(POCKET_ID);
                        }

                        @Override
                        public Optional<RoleMapping> roleMapping(final String providerId) {
                            return providerId.equals(POCKET_ID.id())
                                    ? Optional.of(SetupServiceTest.this.mapping)
                                    : Optional.empty();
                        }

                        @Override
                        public RegistrationMode registrationMode() {
                            return RegistrationMode.APPROVAL;
                        }
                    },
                    () -> new FeatureOverrides(Map.of(Feature.MAP_VIEW, false)),
                    () -> TOKEN,
                    () -> "dulo:\n  print-config: false",
                    new DirectUnitOfWork());

    private static SetupCommand command(final SetupCommand.SetupRole... roles) {
        return new SetupCommand(
                "The Smiths",
                "Since 1820",
                Set.of(Feature.PEOPLE, Feature.MAP_VIEW),
                List.of(roles));
    }

    @Test
    void pendingUntilARootUserExists() {
        assertThat(this.service.isSetupPending()).isTrue();
        this.users.save(Users.user(UserStatus.ACTIVE));
        assertThat(this.service.isSetupPending()).isTrue();

        this.users.save(Users.user(UserStatus.ACTIVE).asRoot());
        assertThat(this.service.isSetupPending()).isFalse();
        assertThat(this.service.isSetupPending()).isFalse();
    }

    @Test
    void completingCreatesTheTreeAndRolesAndMakesTheCallerRoot() {
        final User caller = this.users.save(Users.user(UserStatus.PENDING));
        final Permissions viewPeople = new Permissions(Map.of(Feature.PEOPLE, Set.of(Access.VIEW)));

        final TreeView view =
                this.service.complete(
                        caller.id(),
                        TOKEN,
                        command(
                                new SetupCommand.SetupRole(READERS, "Readers", viewPeople),
                                new SetupCommand.SetupRole(
                                        DefaultRoles.VIEWER, "Guests", Permissions.none())));

        assertThat(view.tree().name()).isEqualTo("The Smiths");
        assertThat(view.features())
                .contains(
                        new FeatureState(Feature.PEOPLE, true, false),
                        new FeatureState(Feature.MAP_VIEW, false, true),
                        new FeatureState(Feature.TIMELINE, false, false));
        assertThat(this.trees.find()).contains(view.tree());
        assertThat(this.roles.findAll())
                .extracting(Role::key)
                .containsExactlyInAnyOrder(
                        DefaultRoles.ADMIN,
                        DefaultRoles.EDITOR,
                        DefaultRoles.CONTRIBUTOR,
                        DefaultRoles.VIEWER,
                        READERS);
        assertThat(this.roles.find(READERS))
                .hasValueSatisfying(role -> {
                    assertThat(role.builtIn()).isFalse();
                    assertThat(role.permissions()).isEqualTo(viewPeople);
                });
        // A built-in role keeps its flag when the setup changes it.
        assertThat(this.roles.find(DefaultRoles.VIEWER))
                .hasValueSatisfying(role -> {
                    assertThat(role.builtIn()).isTrue();
                    assertThat(role.name()).isEqualTo("Guests");
                    assertThat(role.permissions()).isEqualTo(Permissions.none());
                });
        assertThat(this.users.findById(caller.id()))
                .hasValueSatisfying(user -> {
                    assertThat(user.root()).isTrue();
                    assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
                });
        assertThat(this.service.isSetupPending()).isFalse();
    }

    @Test
    void completingReusesATreeStoredWithoutSetup() {
        final TreeSettings existing = TreeSettings.create("Old", null, Set.of());
        this.trees.save(existing);
        final UserId caller = this.users.save(Users.user(UserStatus.ACTIVE)).id();

        final TreeView view = this.service.complete(caller, TOKEN, command());

        assertThat(view.tree().id()).isEqualTo(existing.id());
        assertThat(view.tree().name()).isEqualTo("The Smiths");
    }

    @Test
    void theSetupHappensOnce() {
        final UserId caller = this.users.save(Users.user(UserStatus.ACTIVE)).id();
        this.service.complete(caller, TOKEN, command());

        assertThatThrownBy(() -> this.service.complete(caller, TOKEN, command()))
                .isInstanceOf(SetupCompletedException.class);
        assertThatThrownBy(() -> this.service.defaults(TOKEN))
                .isInstanceOf(SetupCompletedException.class);
        assertThatThrownBy(() -> this.service.configuration(TOKEN))
                .isInstanceOf(SetupCompletedException.class);
    }

    @Test
    void everyStepNeedsTheToken() {
        final UserId caller = this.users.save(Users.user(UserStatus.ACTIVE)).id();

        assertThatThrownBy(() -> this.service.complete(caller, "wrong", command()))
                .isInstanceOf(InvalidSetupTokenException.class);
        assertThatThrownBy(() -> this.service.defaults(""))
                .isInstanceOf(InvalidSetupTokenException.class);
        assertThatThrownBy(() -> this.service.configuration(TOKEN + "x"))
                .isInstanceOf(InvalidSetupTokenException.class);
        assertThat(this.service.isSetupPending()).isTrue();
        assertThat(this.trees.find()).isEmpty();
    }

    @Test
    void rejectsInvalidCommandsWithoutChangingAnything() {
        final UserId caller = this.users.save(Users.user(UserStatus.ACTIVE)).id();
        final SetupCommand.SetupRole readers =
                new SetupCommand.SetupRole(READERS, "Readers", Permissions.none());

        assertThatThrownBy(() -> this.service.complete(caller, TOKEN, command(readers, readers)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defined twice");
        this.mapping = new RoleMapping(Map.of("readers", Set.of(READERS)), null);
        assertThatThrownBy(() -> this.service.complete(caller, TOKEN, command()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'pocketid'")
                .hasMessageContaining("'readers'");
        this.mapping = new RoleMapping(Map.of(), new RoleKey("guests"));
        assertThatThrownBy(() -> this.service.complete(caller, TOKEN, command()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'guests'");
        this.mapping = RoleMapping.none();
        assertThatThrownBy(() ->
                        this.service.complete(
                                caller, TOKEN, new SetupCommand(" ", null, Set.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tree name");
        assertThatThrownBy(() -> this.service.complete(UserId.newId(), TOKEN, command()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(this.service.isSetupPending()).isTrue();
        assertThat(this.roles.findAll()).isEmpty();
    }

    @Test
    void defaultsDescribeTheStartingPoint() {
        final SetupDefaults defaults = this.service.defaults(TOKEN);

        assertThat(defaults.roles()).isEqualTo(DefaultRoles.all());
        assertThat(defaults.features())
                .extracting(FeatureState::feature)
                .doesNotContain(Feature.ADMINISTRATION)
                .contains(Feature.PEOPLE, Feature.AUDIT_LOG);
        assertThat(defaults.features())
                .contains(
                        new FeatureState(Feature.PEOPLE, true, false),
                        new FeatureState(Feature.MAP_VIEW, false, true));
        assertThat(defaults.identityProviders())
                .containsExactly(new SetupDefaults.ProviderRoles(POCKET_ID, this.mapping));
        assertThat(defaults.registrationMode()).isEqualTo(RegistrationMode.APPROVAL);
    }

    @Test
    void theConfigurationNeedsTheToken() {
        assertThat(this.service.configuration(TOKEN)).contains("print-config");
    }
}
