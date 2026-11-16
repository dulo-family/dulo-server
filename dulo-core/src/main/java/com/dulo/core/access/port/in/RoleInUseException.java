package com.dulo.core.access.port.in;

import com.dulo.domain.access.RoleKey;
import com.dulo.domain.user.UserId;

import java.util.ArrayList;
import java.util.List;

/** The role can't be deleted while users have it or an identity provider maps to it. */
public class RoleInUseException extends RuntimeException {

    private final List<UserId> users;
    private final List<String> identityProviders;

    /**
     * @param users the users who have the role, from claims or assigned by hand
     * @param identityProviders the providers whose {@code role-mappings} or {@code default-role}
     *     name the role
     */
    public RoleInUseException(
            final RoleKey key, final List<UserId> users, final List<String> identityProviders) {
        super(message(key, users, identityProviders));
        this.users = List.copyOf(users);
        this.identityProviders = List.copyOf(identityProviders);
    }

    private static String message(
            final RoleKey key, final List<UserId> users, final List<String> identityProviders) {
        final List<String> usedBy = new ArrayList<>();
        if (!users.isEmpty()) {
            usedBy.add(users.size() == 1 ? "1 user has it" : users.size() + " users have it");
        }
        if (!identityProviders.isEmpty()) {
            usedBy.add(
                    "the configuration maps users of %s to it"
                            .formatted(
                                    String.join(
                                            ", ",
                                            identityProviders.stream()
                                                    .map(id -> "'" + id + "'")
                                                    .toList())));
        }
        return "The role '%s' is in use: %s".formatted(key, String.join("; ", usedBy));
    }

    public List<UserId> users() {
        return this.users;
    }

    public List<String> identityProviders() {
        return this.identityProviders;
    }
}
