package com.dulo.adapter.rest.tree;

import com.dulo.core.tree.port.in.TreeView;
import com.dulo.domain.feature.FeatureState;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** A family tree and the state of each feature. */
public record TreeResponse(
        String id,
        String name,
        @Nullable String description,
        long version,
        List<FeatureResponse> features) {

    public static TreeResponse from(final TreeView view) {
        final var tree = view.tree();
        return new TreeResponse(
                tree.id().toString(),
                tree.name(),
                tree.description(),
                tree.version(),
                view.features().stream().map(FeatureResponse::from).toList());
    }

    /** A feature, whether it is on, and whether the configuration forces its state. */
    public record FeatureResponse(String key, boolean enabled, boolean locked) {

        public static FeatureResponse from(final FeatureState state) {
            return new FeatureResponse(state.feature().key(), state.enabled(), state.locked());
        }
    }
}
