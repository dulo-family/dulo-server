package com.dulo.core.testing;

import com.dulo.core.access.port.out.UserRepository;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.user.ExternalIdentity;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps users in a map and checks versions like the real adapter. */
public final class InMemoryUsers implements UserRepository {

    private final Map<UserId, User> users = new LinkedHashMap<>();

    @Override
    public List<User> findAll() {
        return List.copyOf(this.users.values());
    }

    @Override
    public Optional<User> findById(final UserId id) {
        return Optional.ofNullable(this.users.get(id));
    }

    @Override
    public Optional<User> findByExternalIdentity(final ExternalIdentity identity) {
        return this.users.values().stream()
                .filter(u -> u.externalIdentity().equals(identity))
                .findFirst();
    }

    @Override
    public List<User> findAllWithRole(final RoleKey role) {
        return this.users.values().stream().filter(u -> u.roles().contains(role)).toList();
    }

    @Override
    public boolean existsRoot() {
        return this.users.values().stream().anyMatch(User::root);
    }

    @Override
    public User save(final User user) {
        final long actual =
                Optional.ofNullable(this.users.get(user.id())).map(User::version).orElse(0L);
        if (user.version() != actual) {
            throw new ConcurrentModificationException("User", user.id(), user.version(), actual);
        }
        final boolean isNew = !this.users.containsKey(user.id());
        final User stored =
                new User(
                        user.id(),
                        user.externalIdentity(),
                        user.email(),
                        user.displayName(),
                        user.status(),
                        user.root(),
                        user.claimRoles(),
                        user.manualRoles(),
                        user.lastSignIn(),
                        isNew ? 0 : user.version() + 1);
        this.users.put(stored.id(), stored);
        return stored;
    }

    public int size() {
        return this.users.size();
    }
}
