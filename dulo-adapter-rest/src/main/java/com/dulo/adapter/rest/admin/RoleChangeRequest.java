package com.dulo.adapter.rest.admin;

import com.dulo.adapter.rest.access.FeatureAccess;
import com.dulo.core.access.port.in.ManageRolesUseCase.RoleChange;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Left-out values stay as they are. */
record RoleChangeRequest(
        @Nullable String name,
        @Schema(description = "Replaces all of the role's permissions.")
                @Nullable List<FeatureAccess> permissions) {

    /**
     * @throws IllegalArgumentException for unknown features or access
     */
    RoleChange toChange() {
        final List<FeatureAccess> permissions = this.permissions;
        return new RoleChange(
                this.name, permissions == null ? null : FeatureAccess.toPermissions(permissions));
    }
}
