package com.dulo.core.tree.service;

import com.dulo.core.audit.recording.RecordFields;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.tree.TreeSettings;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** The tree settings in the audit history. */
public final class TreeFields implements RecordFields<TreeSettings> {

    public static final TreeFields INSTANCE = new TreeFields();

    private TreeFields() {}

    @Override
    public EntityType entityType() {
        return EntityType.TREE;
    }

    @Override
    public String id(final TreeSettings tree) {
        return tree.id().toString();
    }

    @Override
    public long version(final TreeSettings tree) {
        return tree.version();
    }

    /** Features that can't be switched off are left out: they are always on. */
    @Override
    public Map<String, @Nullable Object> fields(final TreeSettings tree) {
        final Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        fields.put("name", tree.name());
        fields.put("description", tree.description());
        fields.put(
                "enabledFeatures",
                tree.enabledFeatures().stream()
                        .filter(Feature::toggleable)
                        .map(Feature::key)
                        .sorted()
                        .toList());
        return fields;
    }
}
