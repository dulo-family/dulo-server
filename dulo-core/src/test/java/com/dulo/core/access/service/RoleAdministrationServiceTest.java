package com.dulo.core.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.access.port.in.ManageRolesUseCase.NewRole;
import com.dulo.core.access.port.in.ManageRolesUseCase.RoleChange;
import com.dulo.core.access.port.in.RoleExistsException;
import com.dulo.core.access.port.in.RoleInUseException;
import com.dulo.core.identity.port.out.IdentityProvidersPort;
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
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.identity.IdentityProvider;
import com.dulo.domain.identity.IdentityProviderType;
import com.dulo.domain.identity.RegistrationMode;
import com.dulo.domain.identity.RoleMapping;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class RoleAdministrationServiceTest {

    private static final RoleKey READERS = new RoleKey("readers");
    private static final RoleKey GUESTS = new RoleKey("guests");
    private static final Permissions VIEW_PEOPLE =
            new Permissions(Map.of(Feature.PEOPLE, Set.of(Access.VIEW)));

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryRoles roles = new InMemoryRoles(DefaultRoles.all());
    private final InMemoryChangeEvents events = new InMemoryChangeEvents();
    private final RoleAdministrationService service =
            new RoleAdministrationService(
                    this.roles,
                    this.users,
                    new IdentityProvidersPort() {
                        @Override
                        public List<IdentityProvider> providers() {
                            return List.of(
                                    new IdentityProvider(
                                            "pocketid", "Pocket ID", IdentityProviderType.OIDC));
                        }

                        @Override
                        public Optional<RoleMapping> roleMapping(final String providerId) {
                            return Optional.of(
                                    new RoleMapping(Map.of("guests", Set.of(GUESTS)), null));
                        }

                        @Override
                        public RegistrationMode registrationMode() {
                            return RegistrationMode.AUTO;
                        }
                    },
                    new AccessPolicy(
                            this.users,
                            this.roles,
                            new InMemoryTrees(null),
                            FeatureOverrides::none),
                    this.events.recorder(),
                    new DirectUnitOfWork());
    private final UserId admin =
            this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.ADMIN)).id();
    private final UserId root = this.users.save(Users.user(UserStatus.ACTIVE).asRoot()).id();

    @Test
    void administratorsManageTheirOwnRoles() {
        final Role created =
                this.service.createRole(this.admin, new NewRole(READERS, "Readers", VIEW_PEOPLE));
        assertThat(created.builtIn()).isFalse();
        assertThat(this.service.roles(this.admin)).contains(created);
        assertThat(this.service.role(this.admin, READERS)).isEqualTo(created);

        final Role renamed =
                this.service.updateRole(
                        this.admin, READERS, created.version(), new RoleChange("Lurkers", null));
        assertThat(renamed.name()).isEqualTo("Lurkers");
        assertThat(renamed.permissions()).isEqualTo(VIEW_PEOPLE);
        final Role widened =
                this.service.updateRole(
                        this.admin,
                        READERS,
                        renamed.version(),
                        new RoleChange(null, Permissions.all()));
        assertThat(widened.name()).isEqualTo("Lurkers");
        assertThat(widened.permissions()).isEqualTo(Permissions.all());

        this.service.deleteRole(this.admin, READERS, widened.version());
        assertThatThrownBy(() -> this.service.role(this.admin, READERS))
                .isInstanceOf(NotFoundException.class);
        assertThat(this.events.kinds(EntityType.ROLE))
                .containsExactly(
                        ChangeKind.CREATED,
                        ChangeKind.CHANGED,
                        ChangeKind.CHANGED,
                        ChangeKind.DELETED);
        assertThat(this.events.all())
                .allSatisfy(event -> {
                    assertThat(event.actor()).isEqualTo(this.admin);
                    assertThat(event.entityId()).isEqualTo("readers");
                });
        assertThat(this.events.all().get(1).changes())
                .containsExactly(new FieldChange("name", "Readers", "Lurkers"));
        assertThat(this.events.all().get(1).entityVersion()).isEqualTo(renamed.version());
        assertThat(this.events.all().get(3).changes())
                .extracting(FieldChange::field)
                .containsExactly("name", "builtIn", "permissions");
    }

    @Test
    void onlyRootChangesBuiltInRolesAndNobodyDeletesThem() {
        final long version = this.roles.find(DefaultRoles.VIEWER).orElseThrow().version();
        final RoleChange change = new RoleChange("Guests", null);

        assertThatThrownBy(() ->
                        this.service.updateRole(this.admin, DefaultRoles.VIEWER, version, change))
                .isInstanceOf(AccessDeniedException.class);
        final Role changed =
                this.service.updateRole(this.root, DefaultRoles.VIEWER, version, change);
        assertThat(changed.name()).isEqualTo("Guests");
        assertThat(changed.builtIn()).isTrue();
        assertThatThrownBy(() ->
                        this.service.deleteRole(this.root, DefaultRoles.VIEWER, changed.version()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Built-in");
    }

    @Test
    void rolesInUseStay() {
        this.service.createRole(this.admin, new NewRole(READERS, "Readers", VIEW_PEOPLE));
        this.service.createRole(this.admin, new NewRole(GUESTS, "Guests", VIEW_PEOPLE));
        final UserId reader = this.users.save(Users.user(UserStatus.ACTIVE, READERS)).id();
        final UserId guest = this.users.save(Users.user(UserStatus.ACTIVE, GUESTS)).id();

        assertThatThrownBy(() -> this.service.deleteRole(this.admin, READERS, 0))
                .isInstanceOfSatisfying(
                        RoleInUseException.class,
                        e -> {
                            assertThat(e.users()).containsExactly(reader);
                            assertThat(e.identityProviders()).isEmpty();
                            assertThat(e).hasMessageContaining("1 user has it");
                        });
        assertThatThrownBy(() -> this.service.deleteRole(this.admin, GUESTS, 0))
                .isInstanceOfSatisfying(
                        RoleInUseException.class,
                        e -> {
                            assertThat(e.users()).containsExactly(guest);
                            assertThat(e.identityProviders()).containsExactly("pocketid");
                            assertThat(e)
                                    .hasMessageContaining("1 user has it")
                                    .hasMessageContaining("'pocketid'");
                        });
    }

    @Test
    void rejectsDuplicatesStaleVersionsAndUnknownRoles() {
        this.service.createRole(this.admin, new NewRole(READERS, "Readers", VIEW_PEOPLE));

        assertThatThrownBy(() ->
                        this.service.createRole(
                                this.admin, new NewRole(READERS, "Again", VIEW_PEOPLE)))
                .isInstanceOf(RoleExistsException.class);
        assertThatThrownBy(() ->
                        this.service.updateRole(this.admin, READERS, 5, new RoleChange("x", null)))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() -> this.service.deleteRole(this.admin, READERS, 5))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() -> this.service.deleteRole(this.admin, new RoleKey("ghosts"), 0))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyAdministratorsManageRoles() {
        final UserId editor =
                this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.EDITOR)).id();

        assertThatThrownBy(() -> this.service.roles(editor))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.service.createRole(editor, new NewRole(READERS, "x", VIEW_PEOPLE)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> this.service.deleteRole(editor, DefaultRoles.VIEWER, 0))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void partialAdministratorsCantMakeRolesMorePowerfulThanThemselves() {
        // May manage roles and see people, but not more.
        final Role moderators =
                this.roles.save(
                        new Role(
                                new RoleKey("moderators"),
                                "Moderators",
                                false,
                                new Permissions(
                                        Map.of(
                                                Feature.ADMINISTRATION,
                                                Set.of(
                                                        Access.VIEW,
                                                        Access.CREATE,
                                                        Access.EDIT,
                                                        Access.DELETE),
                                                Feature.PEOPLE,
                                                Set.of(Access.VIEW))),
                                0));
        final UserId moderator =
                this.users.save(Users.user(UserStatus.ACTIVE, moderators.key())).id();
        final Role superusers =
                this.service.createRole(
                        this.root,
                        new NewRole(new RoleKey("superusers"), "All", Permissions.all()));

        final Role readers =
                this.service.createRole(moderator, new NewRole(READERS, "Readers", VIEW_PEOPLE));
        assertThat(
                        this.service
                                .updateRole(
                                        moderator,
                                        READERS,
                                        readers.version(),
                                        new RoleChange("People readers", null))
                                .name())
                .isEqualTo("People readers");
        assertThatThrownBy(() ->
                        this.service.createRole(
                                moderator, new NewRole(GUESTS, "Guests", Permissions.all())))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.service.updateRole(
                                moderator,
                                moderators.key(),
                                moderators.version(),
                                new RoleChange(null, Permissions.all())))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("more than you");
        assertThatThrownBy(() ->
                        this.service.updateRole(
                                moderator,
                                superusers.key(),
                                superusers.version(),
                                new RoleChange("Renamed", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.service.deleteRole(moderator, superusers.key(), superusers.version()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
