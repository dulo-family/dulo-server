package com.dulo.adapter.rest.me;

import com.dulo.core.access.port.in.CurrentUserView;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.user.User;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

record MeResponse(
        String id,
        @Nullable String email,
        @Nullable String displayName,
        @Schema(allowableValues = {"active", "pending", "disabled"}) String status,
        @Schema(description = "True for the user who did the initial setup.") boolean root,
        @Schema(
                        description =
                                "Role keys, from the identity provider's claims and assigned by hand.")
                List<String> roles,
        @Schema(
                        description =
                                "What the user may do right now, per enabled feature. Empty"
                                        + " while the account is not active.")
                List<PermissionResponse> permissions) {

    static MeResponse from(final CurrentUserView view) {
        final User user = view.user();
        return new MeResponse(
                user.id().toString(),
                user.email(),
                user.displayName(),
                user.status().name().toLowerCase(Locale.ROOT),
                user.root(),
                user.roles().stream().map(RoleKey::value).sorted().toList(),
                Arrays.stream(Feature.values())
                        .filter(f -> !view.permissions().accessTo(f).isEmpty())
                        .map(f ->
                                new PermissionResponse(
                                        f.key(),
                                        view.permissions().accessTo(f).stream()
                                                .sorted()
                                                .map(Access::key)
                                                .toList()))
                        .toList());
    }

    record PermissionResponse(
            String feature,
            @Schema(description = "Allowed access: view, create, edit, delete.")
                    List<String> access) {}
}
