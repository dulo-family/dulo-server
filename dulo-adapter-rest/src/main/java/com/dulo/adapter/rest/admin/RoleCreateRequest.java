package com.dulo.adapter.rest.admin;

import com.dulo.adapter.rest.access.FeatureAccess;
import com.dulo.core.access.port.in.ManageRolesUseCase.NewRole;
import com.dulo.domain.access.RoleKey;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

record RoleCreateRequest(
        @Schema(description = "Lowercase letters, digits and hyphens.", example = "readers")
                String key,
        String name,
        List<FeatureAccess> permissions) {

    /**
     * @throws IllegalArgumentException for missing values, an invalid key or unknown features
     */
    NewRole toNewRole() {
        if (this.key == null || this.name == null || this.permissions == null) {
            throw new IllegalArgumentException("'key', 'name' and 'permissions' are required");
        }
        return new NewRole(
                new RoleKey(this.key), this.name, FeatureAccess.toPermissions(this.permissions));
    }
}
