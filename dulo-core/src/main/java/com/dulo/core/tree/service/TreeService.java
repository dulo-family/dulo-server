package com.dulo.core.tree.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.tree.port.in.GetTreeUseCase;
import com.dulo.core.tree.port.in.TreeView;
import com.dulo.core.tree.port.out.FeatureOverridesPort;
import com.dulo.core.tree.port.out.TreeSettingsRepository;
import com.dulo.domain.user.UserId;

import java.util.Optional;

public class TreeService implements GetTreeUseCase {

    private final TreeSettingsRepository trees;
    private final FeatureOverridesPort featureOverrides;
    private final AccessPolicy accessPolicy;
    private final UnitOfWork unitOfWork;

    public TreeService(
            final TreeSettingsRepository trees,
            final FeatureOverridesPort featureOverrides,
            final AccessPolicy accessPolicy,
            final UnitOfWork unitOfWork) {
        this.trees = trees;
        this.featureOverrides = featureOverrides;
        this.accessPolicy = accessPolicy;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public Optional<TreeView> getTree(final UserId caller) {
        final var overrides = this.featureOverrides.overrides();
        return this.unitOfWork.read(() -> {
            // Every active user may read the tree: clients need its features to render anything.
            this.accessPolicy.requireActive(caller);
            return this.trees.find().map(tree -> new TreeView(tree, tree.featureStates(overrides)));
        });
    }
}
