package com.dulo.core.testing;

import com.dulo.core.access.port.out.RoleRepository;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Keeps roles in a sorted map. Saving does not check versions. */
public final class InMemoryRoles implements RoleRepository {

    private final Map<String, Role> roles = new TreeMap<>();

    public InMemoryRoles(final List<Role> roles) {
        roles.forEach(this::save);
    }

    @Override
    public List<Role> findAll() {
        return List.copyOf(this.roles.values());
    }

    @Override
    public List<Role> findAllByKeys(final Set<RoleKey> keys) {
        return this.roles.values().stream().filter(r -> keys.contains(r.key())).toList();
    }

    @Override
    public Optional<Role> find(final RoleKey key) {
        return Optional.ofNullable(this.roles.get(key.value()));
    }

    /** Stores the role with the next version. Doesn't check the version it had. */
    @Override
    public Role save(final Role role) {
        final Role stored =
                this.roles.containsKey(role.key().value())
                        ? new Role(
                                role.key(),
                                role.name(),
                                role.builtIn(),
                                role.permissions(),
                                role.version() + 1)
                        : role;
        this.roles.put(role.key().value(), stored);
        return stored;
    }

    @Override
    public void delete(final Role role) {
        this.roles.remove(role.key().value());
    }
}
