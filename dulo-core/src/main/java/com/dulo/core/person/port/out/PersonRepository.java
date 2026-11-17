package com.dulo.core.person.port.out;

import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.shared.PagePosition;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Stores the people of the tree. */
public interface PersonRepository {

    Optional<Person> find(PersonId id);

    /**
     * People sorted by {@link Person#sortName()}, then by id.
     *
     * @param search only people with a name whose {@link
     *     com.dulo.domain.person.PersonName#searchText()} contains this lower-case text, or null
     *     for all
     * @param after only people after this position, or null to start with the first
     * @param limit the maximum number of people
     */
    List<Person> find(@Nullable String search, @Nullable PagePosition after, int limit);

    /**
     * Inserts a new person or updates an existing one.
     *
     * @return the stored person with its new version
     * @throws com.dulo.domain.shared.ConcurrentModificationException if {@code person.version()} is
     *     stale
     */
    Person save(Person person);

    /**
     * @throws com.dulo.domain.shared.ConcurrentModificationException if {@code person.version()} is
     *     stale
     */
    void delete(Person person);
}
