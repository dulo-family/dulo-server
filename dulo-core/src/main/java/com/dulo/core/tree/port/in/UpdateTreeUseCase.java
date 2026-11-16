package com.dulo.core.tree.port.in;

import com.dulo.domain.feature.Feature;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.Set;

public interface UpdateTreeUseCase {

    /**
     * Changes the tree's name, description or enabled features. Needs edit access to the {@code
     * administration} feature.
     *
     * @param expectedVersion the version the caller saw
     * @return the changed tree
     * @throws com.dulo.domain.shared.NotFoundException before the initial setup
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not edit the tree
     */
    TreeView updateTree(UserId caller, long expectedVersion, TreeChange change);

    /**
     * What to change; null leaves a value as it is.
     *
     * @param description the new description; blank removes it
     * @param enabledFeatures the features to switch on, replacing the current ones. Features the
     *     configuration forces keep their forced state.
     */
    record TreeChange(
            @Nullable String name,
            @Nullable String description,
            @Nullable Set<Feature> enabledFeatures) {

        public TreeChange {
            enabledFeatures = enabledFeatures == null ? null : Set.copyOf(enabledFeatures);
        }
    }
}
