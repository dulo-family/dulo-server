package com.dulo.adapter.rest.admin;

import com.dulo.domain.access.RoleKey;
import com.dulo.domain.user.User;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

record AdminUserResponse(
        String id,
        @Schema(description = "The identity provider the user signs in with.") String provider,
        @Nullable String email,
        @Nullable String displayName,
        @Schema(allowableValues = {"active", "pending", "disabled"}) String status,
        @Schema(description = "True for the user who did the initial setup.") boolean root,
        @Schema(description = "Roles mapped from the provider's claims at the last sign-in.")
                List<String> claimRoles,
        @Schema(description = "Roles an administrator assigned.") List<String> manualRoles,
        Instant lastSignIn,
        @Schema(description = "Also the ETag; send it in If-Match to change the user.")
                long version) {

    private static List<String> keys(final Set<RoleKey> roles) {
        return roles.stream().map(RoleKey::value).sorted().toList();
    }

    static AdminUserResponse from(final User user) {
        return new AdminUserResponse(
                user.id().toString(),
                user.externalIdentity().providerId(),
                user.email(),
                user.displayName(),
                user.status().name().toLowerCase(Locale.ROOT),
                user.root(),
                keys(user.claimRoles()),
                keys(user.manualRoles()),
                user.lastSignIn(),
                user.version());
    }
}
