package com.dulo.core.access.port.out;

import com.dulo.domain.access.RoleKey;
import com.dulo.domain.user.ExternalIdentity;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;

import java.util.List;
import java.util.Optional;

/** Stores the users of this instance. */
public interface UserRepository {

    /** All users, oldest first. */
    List<User> findAll();

    Optional<User> findById(UserId id);

    Optional<User> findByExternalIdentity(ExternalIdentity identity);

    /** The users who have the role, from claims or assigned by hand, oldest first. */
    List<User> findAllWithRole(RoleKey role);

    /** Whether a root user exists, i.e. the initial setup is done. */
    boolean existsRoot();

    /**
     * Inserts a new user or updates an existing one.
     *
     * @return the stored user with its new version
     * @throws com.dulo.domain.shared.ConcurrentModificationException if {@code user.version()} is
     *     stale
     */
    User save(User user);
}
