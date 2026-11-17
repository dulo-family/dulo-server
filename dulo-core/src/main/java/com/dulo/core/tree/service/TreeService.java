package com.dulo.core.tree.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.tree.port.in.GetTreeUseCase;
import com.dulo.core.tree.port.in.TreeView;
import com.dulo.core.tree.port.in.UpdateTreeUseCase;
import com.dulo.core.tree.port.out.FeatureOverridesPort;
import com.dulo.core.tree.port.out.TreeSettingsRepository;
import com.dulo.domain.access.Access;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.UserId;

import java.util.Optional;

public class TreeService implements GetTreeUseCase, UpdateTreeUseCase {

    private final TreeSettingsRepository trees;
    private final FeatureOverridesPort featureOverrides;
    private final AccessPolicy accessPolicy;
    private final ChangeRecorder recorder;
    private final UnitOfWork unitOfWork;

    public TreeService(
            final TreeSettingsRepository trees,
            final FeatureOverridesPort featureOverrides,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        this.trees = trees;
        this.featureOverrides = featureOverrides;
        this.accessPolicy = accessPolicy;
        this.recorder = recorder;
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

    @Override
    public TreeView updateTree(
            final UserId caller, final long expectedVersion, final TreeChange change) {
        final var overrides = this.featureOverrides.overrides();
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.ADMINISTRATION, Access.EDIT);
            final TreeSettings tree =
                    this.trees.find().orElseThrow(() -> new NotFoundException("Tree", "tree"));
            if (tree.version() != expectedVersion) {
                throw new ConcurrentModificationException(
                        "Tree", tree.id(), expectedVersion, tree.version());
            }
            final String description = change.description();
            final TreeSettings changed =
                    this.trees.save(
                            new TreeSettings(
                                    tree.id(),
                                    Optional.ofNullable(change.name()).orElse(tree.name()),
                                    description == null ? tree.description() : description,
                                    Optional.ofNullable(change.enabledFeatures())
                                            .orElse(tree.enabledFeatures()),
                                    tree.version()));
            this.recorder.changeSet(caller).changed(TreeFields.INSTANCE, tree, changed);
            return new TreeView(changed, changed.featureStates(overrides));
        });
    }
}
