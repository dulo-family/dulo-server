package com.dulo.adapter.rest.setup;

import com.dulo.adapter.rest.access.RoleResponse;
import com.dulo.adapter.rest.tree.TreeResponse;
import com.dulo.core.setup.port.in.SetupDefaults;
import com.dulo.domain.access.RoleKey;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

record SetupDefaultsResponse(
        @Schema(description = "The built-in roles with their default permissions.")
                List<RoleResponse> roles,
        @Schema(
                        description =
                                "The features that can be switched. Locked features are forced by"
                                        + " the configuration.")
                List<TreeResponse.FeatureResponse> features,
        @Schema(
                        description =
                                "The identity providers and how their claims map to roles. They"
                                        + " come from the configuration and can't be changed here.")
                List<SetupIdentityProviderResponse> identityProviders,
        @Schema(
                        description =
                                "Whether new users are active right away (`auto`) or wait for"
                                        + " approval (`approval`).",
                        allowableValues = {"auto", "approval"})
                String registrationMode) {

    static SetupDefaultsResponse from(final SetupDefaults defaults) {
        return new SetupDefaultsResponse(
                defaults.roles().stream().map(RoleResponse::from).toList(),
                defaults.features().stream().map(TreeResponse.FeatureResponse::from).toList(),
                defaults.identityProviders().stream()
                        .map(SetupIdentityProviderResponse::from)
                        .toList(),
                defaults.registrationMode().name().toLowerCase(Locale.ROOT));
    }

    record SetupIdentityProviderResponse(
            String id,
            String displayName,
            @Schema(allowableValues = {"oidc", "google", "microsoft"}) String type,
            @Schema(description = "Claim value to the role keys it grants.")
                    Map<String, List<String>> roleMappings,
            @Schema(description = "The role of users whose claims match no mapping.")
                    @Nullable String defaultRole) {

        static SetupIdentityProviderResponse from(final SetupDefaults.ProviderRoles provider) {
            final Map<String, List<String>> mappings = new TreeMap<>();
            provider.roleMapping()
                    .byClaimValue()
                    .forEach((value, roles) ->
                            mappings.put(
                                    value, roles.stream().map(RoleKey::value).sorted().toList()));
            return new SetupIdentityProviderResponse(
                    provider.provider().id(),
                    provider.provider().displayName(),
                    provider.provider().type().key(),
                    mappings,
                    provider.roleMapping().defaultRoleIfAny().map(RoleKey::value).orElse(null));
        }
    }
}
