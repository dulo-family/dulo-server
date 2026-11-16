package com.dulo.adapter.rest.access;

import com.dulo.domain.access.Role;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A role and what it may do. */
public record RoleResponse(
        @Schema(description = "Lowercase letters, digits and hyphens.", example = "editor")
                String key,
        String name,
        @Schema(description = "Admin, editor, contributor and viewer are built in.")
                boolean builtIn,
        List<FeatureAccess> permissions,
        @Schema(description = "Also the ETag; send it in If-Match to change the role.")
                long version) {

    public static RoleResponse from(final Role role) {
        return new RoleResponse(
                role.key().value(),
                role.name(),
                role.builtIn(),
                FeatureAccess.from(role.permissions()),
                role.version());
    }
}
