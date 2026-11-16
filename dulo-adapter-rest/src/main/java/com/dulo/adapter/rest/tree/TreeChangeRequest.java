package com.dulo.adapter.rest.tree;

import com.dulo.core.tree.port.in.UpdateTreeUseCase.TreeChange;
import com.dulo.domain.feature.Feature;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Left-out values stay as they are. */
record TreeChangeRequest(
        @Schema(description = "1-200 characters.") @Nullable String name,
        @Schema(description = "At most 2000 characters. An empty string removes it.")
                @Nullable String description,
        @Schema(
                        description =
                                "Keys of the features to switch on, replacing the current ones."
                                        + " Features the configuration forces keep their forced"
                                        + " state.")
                @Nullable List<String> enabledFeatures) {

    /**
     * @throws IllegalArgumentException for an unknown feature
     */
    TreeChange toChange() {
        final List<String> keys = this.enabledFeatures;
        final Set<Feature> features =
                keys == null
                        ? null
                        : keys.stream()
                                .map(key ->
                                        Feature.fromKey(key)
                                                .orElseThrow(() ->
                                                        new IllegalArgumentException(
                                                                "Unknown feature '%s'"
                                                                        .formatted(key))))
                                .collect(Collectors.toSet());
        return new TreeChange(this.name, this.description, features);
    }
}
