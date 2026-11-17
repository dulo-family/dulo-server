package com.dulo.core.access.service;

import com.dulo.core.audit.recording.RecordFields;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.user.User;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Users in the audit history. The time of the last sign-in is left out: it changes at every sign-in
 * and would bury the real changes.
 */
public final class UserFields implements RecordFields<User> {

    public static final UserFields INSTANCE = new UserFields();

    private UserFields() {}

    private static List<String> keys(final Set<RoleKey> roles) {
        return roles.stream().map(RoleKey::value).sorted().toList();
    }

    @Override
    public EntityType entityType() {
        return EntityType.USER;
    }

    @Override
    public String id(final User user) {
        return user.id().toString();
    }

    @Override
    public long version(final User user) {
        return user.version();
    }

    @Override
    public Map<String, @Nullable Object> fields(final User user) {
        final Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        fields.put("identityProvider", user.externalIdentity().providerId());
        fields.put("externalSubject", user.externalIdentity().subject());
        fields.put("email", user.email());
        fields.put("displayName", user.displayName());
        fields.put("status", user.status().name().toLowerCase(Locale.ROOT));
        fields.put("root", user.root());
        fields.put("claimRoles", keys(user.claimRoles()));
        fields.put("manualRoles", keys(user.manualRoles()));
        return fields;
    }
}
