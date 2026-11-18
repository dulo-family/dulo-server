package com.dulo.core.family.port.out;

import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.shared.PagePosition;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Stores the families of the tree. */
public interface FamilyRepository {

    Optional<Family> find(FamilyId id);

    /**
     * Families sorted by id, which is the order they were created in, to the millisecond.
     *
     * @param member only families with this person as a partner or child, or null for all
     * @param after only families after this position, whose sort key is the id; null to start with
     *     the first
     * @param limit the maximum number of families
     */
    List<Family> find(@Nullable PersonId member, @Nullable PagePosition after, int limit);

    /** All families with the person as a partner or child, sorted by id. */
    List<Family> findWithMember(PersonId member);

    /**
     * Inserts a new family or updates an existing one.
     *
     * @return the stored family with its new version
     * @throws com.dulo.domain.shared.ConcurrentModificationException if {@code family.version()} is
     *     stale
     */
    Family save(Family family);

    /**
     * @throws com.dulo.domain.shared.ConcurrentModificationException if {@code family.version()} is
     *     stale
     */
    void delete(Family family);
}
