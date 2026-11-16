package com.dulo.core.testing;

import com.dulo.domain.access.RoleKey;
import com.dulo.domain.user.ExternalIdentity;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import java.time.Instant;
import java.util.Set;

/** Builds users for tests. */
public final class Users {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private Users() {}

    /** A new user with the given status and manual roles, not stored yet. */
    public static User user(final UserStatus status, final RoleKey... roles) {
        return User.register(
                        new ExternalIdentity("test", UserId.newId().toString()),
                        null,
                        null,
                        status,
                        Set.of(),
                        NOW)
                .withManualRoles(Set.of(roles));
    }
}
