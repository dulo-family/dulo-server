package com.dulo.adapter.rest.admin;

import com.dulo.core.access.port.in.ManageUsersUseCase.UserChange;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.user.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Left-out values stay as they are. */
record UserChangeRequest(
        @Schema(
                        description =
                                "`active` approves a pending user or reactivates a disabled one.",
                        allowableValues = {"active", "pending", "disabled"})
                @Nullable String status,
        @Schema(description = "Role keys, replacing the roles an administrator assigned.")
                @Nullable List<String> manualRoles) {

    /**
     * @throws IllegalArgumentException for an unknown status or an invalid role key
     */
    UserChange toChange() {
        final String status = this.status;
        final UserStatus newStatus =
                status == null
                        ? null
                        : Arrays.stream(UserStatus.values())
                                .filter(s -> s.name().equalsIgnoreCase(status))
                                .findFirst()
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "Unknown status '%s'".formatted(status)));
        final List<String> roles = this.manualRoles;
        final Set<RoleKey> manualRoles =
                roles == null ? null : roles.stream().map(RoleKey::new).collect(Collectors.toSet());
        return new UserChange(newStatus, manualRoles);
    }
}
