package com.dulo.core.access.port.in;

import com.dulo.domain.access.RoleKey;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Lists users and changes their status and manually assigned roles. Needs the {@code
 * administration} feature: view to read, edit to change.
 */
public interface ManageUsersUseCase {

    /**
     * All users, oldest first.
     *
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not view users
     */
    List<User> users(UserId caller);

    /**
     * @throws com.dulo.domain.shared.NotFoundException if the user doesn't exist
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not view users
     */
    User user(UserId caller, UserId id);

    /**
     * Approves, disables or reactivates a user, or replaces their manual roles.
     *
     * @param expectedVersion the version the caller saw
     * @return the changed user
     * @throws com.dulo.domain.shared.NotFoundException if the user doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws IllegalArgumentException for an unknown role, a change to the caller's own status, or
     *     a status other than active for the root user
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not edit users, or,
     *     unless they are root, the user may do more than the caller or an added role grants more
     *     than the caller has
     */
    User updateUser(UserId caller, UserId id, long expectedVersion, UserChange change);

    /**
     * What to change; null leaves a value as it is.
     *
     * @param status the new status
     * @param manualRoles the roles an administrator assigns, replacing the current ones. Roles from
     *     the identity provider's claims can't be changed here.
     */
    record UserChange(@Nullable UserStatus status, @Nullable Set<RoleKey> manualRoles) {

        public UserChange {
            manualRoles = manualRoles == null ? null : Set.copyOf(manualRoles);
        }
    }
}
