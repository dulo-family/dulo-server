package com.dulo.adapter.rest.setup;

import com.dulo.adapter.rest.access.FeatureAccess;
import com.dulo.core.setup.port.in.SetupCommand;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

record SetupRequest(
        @Schema(description = "The tree's name, 1-200 characters.", example = "The Smiths")
                String name,
        @Schema(description = "At most 2000 characters.") @Nullable String description,
        @Schema(
                        description =
                                "Keys of the features to switch on. Features the configuration"
                                        + " forces keep their forced state.",
                        example = "[\"people\", \"families\", \"tree-view\"]")
                List<String> enabledFeatures,
        @Schema(
                        description =
                                "Roles to create or change. Built-in roles that are left out get"
                                        + " their default permissions. Every role an identity"
                                        + " provider maps to must exist.")
                @Nullable List<RoleRequest> roles) {

    private static <T> T required(final @Nullable T value, final String name) {
        if (value == null) {
            throw new IllegalArgumentException("'%s' is required".formatted(name));
        }
        return value;
    }

    /**
     * The use case's command.
     *
     * @throws IllegalArgumentException for missing values and unknown keys
     */
    SetupCommand toCommand() {
        final Set<Feature> features =
                required(this.enabledFeatures, "enabledFeatures").stream()
                        .map(key ->
                                Feature.fromKey(key)
                                        .orElseThrow(() ->
                                                new IllegalArgumentException(
                                                        "Unknown feature '%s'".formatted(key))))
                        .collect(Collectors.toSet());
        final List<SetupCommand.SetupRole> roles =
                this.roles == null
                        ? List.of()
                        : this.roles.stream().map(RoleRequest::toSetupRole).toList();
        return new SetupCommand(required(this.name, "name"), this.description, features, roles);
    }

    record RoleRequest(
            @Schema(description = "Lowercase letters, digits and hyphens.", example = "readers")
                    String key,
            String name,
            List<FeatureAccess> permissions) {

        SetupCommand.SetupRole toSetupRole() {
            return new SetupCommand.SetupRole(
                    new RoleKey(required(this.key, "roles[].key")),
                    required(this.name, "roles[].name"),
                    FeatureAccess.toPermissions(required(this.permissions, "roles[].permissions")));
        }
    }
}
