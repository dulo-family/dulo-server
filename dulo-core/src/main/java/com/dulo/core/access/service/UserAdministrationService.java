package com.dulo.core.access.service;

import com.dulo.core.access.port.in.ManageUsersUseCase;
import com.dulo.core.access.port.out.RoleRepository;
import com.dulo.core.access.port.out.UserRepository;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.user.Actor;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserAdministrationService implements ManageUsersUseCase {

    private final UserRepository users;
    private final RoleRepository roles;
    private final AccessPolicy accessPolicy;
    private final UnitOfWork unitOfWork;

    public UserAdministrationService(
            final UserRepository users,
            final RoleRepository roles,
            final AccessPolicy accessPolicy,
            final UnitOfWork unitOfWork) {
        this.users = users;
        this.roles = roles;
        this.accessPolicy = accessPolicy;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public List<User> users(final UserId caller) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.ADMINISTRATION, Access.VIEW);
            return this.users.findAll();
        });
    }

    @Override
    public User user(final UserId caller, final UserId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.ADMINISTRATION, Access.VIEW);
            return this.find(id);
        });
    }

    @Override
    public User updateUser(
            final UserId caller,
            final UserId id,
            final long expectedVersion,
            final UserChange change) {
        return this.unitOfWork.write(() -> {
            final Actor actor =
                    this.accessPolicy.require(caller, Feature.ADMINISTRATION, Access.EDIT);
            final User user = this.find(id);
            if (user.version() != expectedVersion) {
                throw new ConcurrentModificationException(
                        "User", id, expectedVersion, user.version());
            }
            final Set<RoleKey> manualRoles = change.manualRoles();
            if (manualRoles != null) {
                this.requireExisting(manualRoles);
            }
            if (!actor.root()) {
                this.requireWithinOwnPermissions(actor, user, manualRoles);
            }
            final User withStatus = this.withStatus(caller, user, change.status());
            return this.users.save(
                    manualRoles == null ? withStatus : withStatus.withManualRoles(manualRoles));
        });
    }

    private User withStatus(
            final UserId caller, final User user, final @Nullable UserStatus status) {
        if (status == null || status == user.status()) {
            return user;
        }
        if (user.id().equals(caller)) {
            // Disabling yourself locks you out; the next administrator would have to undo it.
            throw new IllegalArgumentException("You can't change your own status");
        }
        if (user.root()) {
            throw new IllegalArgumentException("The root user is always active");
        }
        return user.withStatus(status);
    }

    /**
     * Keeps partial administrators partial. Without it, someone allowed only to approve users could
     * give themselves the admin role, or approve someone who may do more than they may.
     *
     * @param manualRoles the new manual roles, or null when they don't change
     */
    private void requireWithinOwnPermissions(
            final Actor actor, final User user, final @Nullable Set<RoleKey> manualRoles) {
        if (!actor.permissions().covers(this.accessPolicy.grantedTo(user))) {
            throw new AccessDeniedException(
                    AccessDeniedException.Reason.NOT_PERMITTED,
                    "This user may do more than you, so only root or an administrator with at"
                            + " least their permissions may change them");
        }
        if (manualRoles == null) {
            return;
        }
        final Set<RoleKey> added = new HashSet<>(manualRoles);
        added.removeAll(user.manualRoles());
        this.roles.findAllByKeys(added).stream()
                .filter(role -> !actor.permissions().covers(role.permissions()))
                .map(Role::key)
                .min(Comparator.comparing(RoleKey::value))
                .ifPresent(key -> {
                    throw new AccessDeniedException(
                            AccessDeniedException.Reason.NOT_PERMITTED,
                            "The role '%s' grants more than you may do, so you can't assign it"
                                    .formatted(key));
                });
    }

    private User find(final UserId id) {
        return this.users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private void requireExisting(final Set<RoleKey> keys) {
        final Set<RoleKey> existing =
                this.roles.findAllByKeys(keys).stream().map(Role::key).collect(Collectors.toSet());
        keys.stream()
                .filter(key -> !existing.contains(key))
                .min(Comparator.comparing(RoleKey::value))
                .ifPresent(key -> {
                    throw new IllegalArgumentException("Unknown role '%s'".formatted(key));
                });
    }
}
