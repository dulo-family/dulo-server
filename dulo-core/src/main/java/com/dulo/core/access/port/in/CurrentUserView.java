package com.dulo.core.access.port.in;

import com.dulo.domain.access.Permissions;
import com.dulo.domain.user.User;

import java.util.Objects;

/**
 * The signed-in user.
 *
 * @param permissions what the user may do right now: their roles' permissions on enabled features,
 *     or nothing while they are not active
 */
public record CurrentUserView(User user, Permissions permissions) {

    public CurrentUserView {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(permissions, "permissions");
    }
}
