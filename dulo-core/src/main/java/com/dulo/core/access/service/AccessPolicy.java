package com.dulo.core.access.service;

import com.dulo.core.access.port.out.RoleRepository;
import com.dulo.core.access.port.out.UserRepository;
import com.dulo.core.tree.port.out.FeatureOverridesPort;
import com.dulo.core.tree.port.out.TreeSettingsRepository;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.AccessDeniedException.Reason;
import com.dulo.domain.access.Permissions;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.user.Actor;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether a user may perform an operation. Every use case calls it, so all APIs share the
 * same rules. Permissions are read from the database on every call, so changes to roles take effect
 * immediately. Call it inside the use case's unit of work.
 */
public class AccessPolicy {

    private final UserRepository users;
    private final RoleRepository roles;
    private final TreeSettingsRepository trees;
    private final FeatureOverridesPort featureOverrides;

    public AccessPolicy(
            final UserRepository users,
            final RoleRepository roles,
            final TreeSettingsRepository trees,
            final FeatureOverridesPort featureOverrides) {
        this.users = users;
        this.roles = roles;
        this.trees = trees;
        this.featureOverrides = featureOverrides;
    }

    /**
     * Checks that the user is active, the feature is enabled, and the user is root or one of their
     * roles grants the access.
     *
     * @return the user with their resolved permissions
     * @throws AccessDeniedException if any check fails
     */
    public Actor require(final UserId userId, final Feature feature, final Access access) {
        final Actor actor = this.requireActive(userId);
        if (!this.isEnabled(feature)) {
            throw new AccessDeniedException(
                    Reason.FEATURE_DISABLED,
                    "The feature '%s' is disabled".formatted(feature.key()));
        }
        if (!actor.root() && !actor.permissions().allows(feature, access)) {
            throw new AccessDeniedException(
                    Reason.NOT_PERMITTED,
                    "Your roles do not allow %s access to '%s'"
                            .formatted(access.key(), feature.key()));
        }
        return actor;
    }

    /**
     * Checks only that the user exists and is active.
     *
     * @return the user with their resolved permissions
     * @throws AccessDeniedException if the user is unknown, pending or disabled
     */
    public Actor requireActive(final UserId userId) {
        final User user =
                this.users
                        .findById(userId)
                        .orElseThrow(() ->
                                new AccessDeniedException(
                                        Reason.UNKNOWN_USER, "The user does not exist"));
        switch (user.status()) {
            case PENDING ->
                    throw new AccessDeniedException(
                            Reason.ACCOUNT_PENDING,
                            "Your account is waiting for an administrator's approval");
            case DISABLED ->
                    throw new AccessDeniedException(
                            Reason.ACCOUNT_DISABLED, "Your account is disabled");
            case ACTIVE -> {
                // Allowed; the permissions decide the rest.
            }
        }
        if (user.root()) {
            return new Actor(user.id(), true, Permissions.all());
        }
        return new Actor(user.id(), false, this.rolePermissions(user));
    }

    /**
     * What the user may do right now: nothing unless they are active, everything on enabled
     * features for root, and otherwise their roles' permissions on enabled features.
     */
    public Permissions effectivePermissions(final User user) {
        if (user.status() != UserStatus.ACTIVE) {
            return Permissions.none();
        }
        final Permissions granted = user.root() ? Permissions.all() : this.rolePermissions(user);
        final Map<Feature, Set<Access>> enabled = new EnumMap<>(Feature.class);
        granted.grants()
                .forEach((feature, access) -> {
                    if (this.isEnabled(feature)) {
                        enabled.put(feature, access);
                    }
                });
        return new Permissions(enabled);
    }

    /**
     * What a user's roles grant, whatever their status and whether the features are enabled:
     * everything for root.
     */
    public Permissions grantedTo(final User user) {
        return user.root() ? Permissions.all() : this.rolePermissions(user);
    }

    /** What the roles grant together; roles that don't exist grant nothing. */
    public Permissions grantedBy(final Set<RoleKey> roles) {
        return this.roles.findAllByKeys(roles).stream()
                .map(Role::permissions)
                .reduce(Permissions.none(), Permissions::union);
    }

    private Permissions rolePermissions(final User user) {
        return this.grantedBy(user.roles());
    }

    private boolean isEnabled(final Feature feature) {
        final FeatureOverrides overrides = this.featureOverrides.overrides();
        return this.trees
                .find()
                .map(tree -> tree.isEnabled(feature, overrides))
                // Before the initial setup only forced and always-on features are enabled.
                .orElseGet(() ->
                        !feature.toggleable() || overrides.forcedState(feature).orElse(false));
    }
}
