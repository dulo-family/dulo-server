package com.dulo.core.setup.port.in;

import com.dulo.domain.access.Permissions;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What the initial setup creates.
 *
 * @param treeName the family tree's name
 * @param treeDescription an optional description of the tree
 * @param enabledFeatures the features to switch on; features the configuration forces keep the
 *     forced state
 * @param roles roles to store. Built-in roles that are left out get their default permissions.
 */
public record SetupCommand(
        String treeName,
        @Nullable String treeDescription,
        Set<Feature> enabledFeatures,
        List<SetupRole> roles) {

    public SetupCommand {
        Objects.requireNonNull(treeName, "treeName");
        enabledFeatures = Set.copyOf(enabledFeatures);
        roles = List.copyOf(roles);
    }

    /** A role and what it may do. Whether it is built in follows from its key. */
    public record SetupRole(RoleKey key, String name, Permissions permissions) {

        public SetupRole {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(permissions, "permissions");
        }
    }
}
