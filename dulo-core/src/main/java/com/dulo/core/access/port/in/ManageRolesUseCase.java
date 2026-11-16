package com.dulo.core.access.port.in;

import com.dulo.domain.access.Permissions;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Lists, creates, changes and deletes roles. Needs the {@code administration} feature with the
 * matching access. Only the root user may change built-in roles, and nobody may delete them.
 * Callers other than root can only create, change or delete roles whose permissions they have
 * themselves, so a partial administrator can't widen a role into a full one.
 */
public interface ManageRolesUseCase {

    /**
     * All roles, sorted by key.
     *
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not view roles
     */
    List<Role> roles(UserId caller);

    /**
     * @throws com.dulo.domain.shared.NotFoundException if the role doesn't exist
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not view roles
     */
    Role role(UserId caller, RoleKey key);

    /**
     * @throws RoleExistsException if a role with the key exists
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not create roles
     */
    Role createRole(UserId caller, NewRole role);

    /**
     * @param expectedVersion the version the caller saw
     * @throws com.dulo.domain.shared.NotFoundException if the role doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not edit roles, or the
     *     role is built in and the caller isn't root
     */
    Role updateRole(UserId caller, RoleKey key, long expectedVersion, RoleChange change);

    /**
     * @param expectedVersion the version the caller saw
     * @throws com.dulo.domain.shared.NotFoundException if the role doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws IllegalArgumentException if the role is built in
     * @throws RoleInUseException if a user has the role or an identity provider maps to it
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not delete roles
     */
    void deleteRole(UserId caller, RoleKey key, long expectedVersion);

    record NewRole(RoleKey key, String name, Permissions permissions) {

        public NewRole {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(permissions, "permissions");
        }
    }

    /** What to change; null leaves a value as it is. */
    record RoleChange(@Nullable String name, @Nullable Permissions permissions) {}
}
