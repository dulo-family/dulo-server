package com.dulo.domain.identity;

import com.dulo.domain.access.RoleKey;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How an identity provider's role claim translates to Dulo roles. Each claim value maps to one
 * role; a user with several matching values gets each of their roles.
 *
 * @param byClaimValue the Dulo role per value of the provider's roles claim, e.g. {@code
 *     family-admins -> admin}
 * @param defaultRole the role of users whose claims match no mapping
 */
public record RoleMapping(Map<String, RoleKey> byClaimValue, @Nullable RoleKey defaultRole) {

    public RoleMapping {
        byClaimValue = Map.copyOf(byClaimValue);
    }

    public static RoleMapping none() {
        return new RoleMapping(Map.of(), null);
    }

    /** The roles the given claim values map to, empty when no mapping matches. */
    public Set<RoleKey> mappedRoles(final Set<String> claimValues) {
        return claimValues.stream()
                .map(this.byClaimValue::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<RoleKey> defaultRoleIfAny() {
        return Optional.ofNullable(defaultRole);
    }
}
