package com.dulo.core.access.service;

import com.dulo.core.audit.recording.RecordFields;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.Role;
import com.dulo.domain.audit.EntityType;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Roles in the audit history. */
public final class RoleFields implements RecordFields<Role> {

    public static final RoleFields INSTANCE = new RoleFields();

    private RoleFields() {}

    @Override
    public EntityType entityType() {
        return EntityType.ROLE;
    }

    @Override
    public String id(final Role role) {
        return role.key().value();
    }

    @Override
    public long version(final Role role) {
        return role.version();
    }

    /** Permissions are a map from feature key to the sorted access keys. */
    @Override
    public Map<String, @Nullable Object> fields(final Role role) {
        final Map<String, List<String>> permissions = new TreeMap<>();
        role.permissions()
                .grants()
                .forEach((feature, accesses) ->
                        permissions.put(
                                feature.key(),
                                accesses.stream().sorted().map(Access::key).toList()));
        final Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        fields.put("name", role.name());
        fields.put("builtIn", role.builtIn());
        fields.put("permissions", permissions);
        return fields;
    }
}
