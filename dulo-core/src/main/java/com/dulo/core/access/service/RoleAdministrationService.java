package com.dulo.core.access.service;

import com.dulo.core.access.port.in.ManageRolesUseCase;
import com.dulo.core.access.port.in.RoleExistsException;
import com.dulo.core.access.port.in.RoleInUseException;
import com.dulo.core.access.port.out.RoleRepository;
import com.dulo.core.access.port.out.UserRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.identity.port.out.IdentityProvidersPort;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.Permissions;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.identity.IdentityProvider;
import com.dulo.domain.identity.RoleMapping;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.user.Actor;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public class RoleAdministrationService implements ManageRolesUseCase {

    private final RoleRepository roles;
    private final UserRepository users;
    private final IdentityProvidersPort providers;
    private final AccessPolicy accessPolicy;
    private final ChangeRecorder recorder;
    private final UnitOfWork unitOfWork;

    public RoleAdministrationService(
            final RoleRepository roles,
            final UserRepository users,
            final IdentityProvidersPort providers,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        this.roles = roles;
        this.users = users;
        this.providers = providers;
        this.accessPolicy = accessPolicy;
        this.recorder = recorder;
        this.unitOfWork = unitOfWork;
    }

    private static void checkVersion(final Role role, final long expectedVersion) {
        if (role.version() != expectedVersion) {
            throw new ConcurrentModificationException(
                    "Role", role.key(), expectedVersion, role.version());
        }
    }

    /**
     * Keeps partial administrators partial: without it, someone allowed only to edit roles could
     * give a role they have every permission. Root may do anything.
     */
    private static void requireWithinOwnPermissions(
            final Actor actor, final Permissions permissions) {
        if (!actor.root() && !actor.permissions().covers(permissions)) {
            throw new AccessDeniedException(
                    AccessDeniedException.Reason.NOT_PERMITTED,
                    "A role can't grant more than you may do yourself");
        }
    }

    @Override
    public List<Role> roles(final UserId caller) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.ADMINISTRATION, Access.VIEW);
            return this.roles.findAll();
        });
    }

    @Override
    public Role role(final UserId caller, final RoleKey key) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.ADMINISTRATION, Access.VIEW);
            return this.find(key);
        });
    }

    @Override
    public Role createRole(final UserId caller, final NewRole role) {
        return this.unitOfWork.write(() -> {
            final Actor actor =
                    this.accessPolicy.require(caller, Feature.ADMINISTRATION, Access.CREATE);
            if (this.roles.find(role.key()).isPresent()) {
                throw new RoleExistsException(role.key());
            }
            requireWithinOwnPermissions(actor, role.permissions());
            final Role created =
                    this.roles.save(
                            new Role(role.key(), role.name(), false, role.permissions(), 0));
            this.recorder.changeSet(caller).created(RoleFields.INSTANCE, created);
            return created;
        });
    }

    @Override
    public Role updateRole(
            final UserId caller,
            final RoleKey key,
            final long expectedVersion,
            final RoleChange change) {
        return this.unitOfWork.write(() -> {
            final Actor actor =
                    this.accessPolicy.require(caller, Feature.ADMINISTRATION, Access.EDIT);
            final Role role = this.find(key);
            checkVersion(role, expectedVersion);
            if (role.builtIn() && !actor.root()) {
                throw new AccessDeniedException(
                        AccessDeniedException.Reason.NOT_PERMITTED,
                        "Only the root user may change built-in roles");
            }
            requireWithinOwnPermissions(actor, role.permissions());
            final Permissions permissions = change.permissions();
            if (permissions != null) {
                requireWithinOwnPermissions(actor, permissions);
            }
            final Role changed =
                    this.roles.save(
                            new Role(
                                    key,
                                    Optional.ofNullable(change.name()).orElse(role.name()),
                                    role.builtIn(),
                                    permissions == null ? role.permissions() : permissions,
                                    role.version()));
            this.recorder.changeSet(caller).changed(RoleFields.INSTANCE, role, changed);
            return changed;
        });
    }

    @Override
    public void deleteRole(final UserId caller, final RoleKey key, final long expectedVersion) {
        this.unitOfWork.write(() -> {
            final Actor actor =
                    this.accessPolicy.require(caller, Feature.ADMINISTRATION, Access.DELETE);
            final Role role = this.find(key);
            checkVersion(role, expectedVersion);
            requireWithinOwnPermissions(actor, role.permissions());
            if (role.builtIn()) {
                throw new IllegalArgumentException("Built-in roles can't be deleted");
            }
            final List<UserId> holders =
                    this.users.findAllWithRole(key).stream().map(User::id).toList();
            final List<String> mappedBy =
                    this.providers.providers().stream()
                            .map(IdentityProvider::id)
                            .filter(id -> this.mapsTo(id, key))
                            .toList();
            if (!holders.isEmpty() || !mappedBy.isEmpty()) {
                throw new RoleInUseException(key, holders, mappedBy);
            }
            this.roles.delete(role);
            this.recorder.changeSet(caller).deleted(RoleFields.INSTANCE, role, List.of());
            return role;
        });
    }

    /** Whether the provider's configuration maps users to the role. */
    private boolean mapsTo(final String providerId, final RoleKey key) {
        final RoleMapping mapping =
                this.providers.roleMapping(providerId).orElse(RoleMapping.none());
        return mapping.defaultRoleIfAny().filter(key::equals).isPresent()
                || mapping.byClaimValue().values().stream().anyMatch(roles -> roles.contains(key));
    }

    private Role find(final RoleKey key) {
        return this.roles.find(key).orElseThrow(() -> new NotFoundException("Role", key));
    }
}
