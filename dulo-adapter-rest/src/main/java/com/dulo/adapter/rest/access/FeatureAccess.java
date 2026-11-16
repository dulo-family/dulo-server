package com.dulo.adapter.rest.access;

import com.dulo.domain.access.Access;
import com.dulo.domain.access.Permissions;
import com.dulo.domain.feature.Feature;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The access granted on one feature. */
public record FeatureAccess(
        @Schema(description = "Feature key, e.g. `people`.", example = "people") String feature,
        @Schema(description = "Allowed access: view, create, edit, delete.") List<String> access) {

    /** One entry per feature with any access, in feature order. */
    public static List<FeatureAccess> from(final Permissions permissions) {
        return Arrays.stream(Feature.values())
                .filter(f -> !permissions.accessTo(f).isEmpty())
                .map(f ->
                        new FeatureAccess(
                                f.key(),
                                permissions.accessTo(f).stream()
                                        .sorted()
                                        .map(Access::key)
                                        .toList()))
                .toList();
    }

    /**
     * Parses entries into permissions. Entries for the same feature are merged.
     *
     * @throws IllegalArgumentException for an unknown feature or access key
     */
    public static Permissions toPermissions(final List<FeatureAccess> entries) {
        final Map<Feature, Set<Access>> grants = new EnumMap<>(Feature.class);
        for (final FeatureAccess entry : entries) {
            final Feature feature =
                    Feature.fromKey(entry.feature())
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Unknown feature '%s'".formatted(entry.feature())));
            final Set<Access> access =
                    grants.computeIfAbsent(feature, _ -> EnumSet.noneOf(Access.class));
            for (final String key : entry.access()) {
                access.add(
                        Access.fromKey(key)
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "Unknown access '%s'".formatted(key))));
            }
        }
        return new Permissions(grants);
    }
}
