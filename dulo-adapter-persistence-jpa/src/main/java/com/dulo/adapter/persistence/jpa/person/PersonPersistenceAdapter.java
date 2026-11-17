package com.dulo.adapter.persistence.jpa.person;

import com.dulo.adapter.persistence.jpa.shared.Keyset;
import com.dulo.core.person.port.out.PersonRepository;
import com.dulo.domain.person.NameType;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonName;
import com.dulo.domain.person.Sex;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.PagePosition;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PersonPersistenceAdapter implements PersonRepository {

    private final PersonJpaRepository repository;
    private final EntityManager entityManager;

    PersonPersistenceAdapter(
            final PersonJpaRepository repository, final EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    private static void checkVersion(final Person person, final long actual) {
        if (person.version() != actual) {
            throw new ConcurrentModificationException(
                    "Person", person.id(), person.version(), actual);
        }
    }

    private static PersonNameRow row(final PersonName name) {
        return new PersonNameRow(
                name.type().key(),
                name.given(),
                name.surname(),
                name.prefix(),
                name.suffix(),
                name.nickname(),
                name.searchText());
    }

    private static PersonName toDomain(final PersonNameRow row) {
        return new PersonName(
                NameType.fromKey(row.type())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Unknown name type '%s'".formatted(row.type()))),
                row.given(),
                row.surname(),
                row.prefix(),
                row.suffix(),
                row.nickname());
    }

    private static Person toDomain(final PersonJpaEntity entity) {
        return new Person(
                PersonId.parse(entity.getId()),
                entity.getNames().stream().map(PersonPersistenceAdapter::toDomain).toList(),
                Sex.fromKey(entity.getSex())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Unknown sex '%s'".formatted(entity.getSex()))),
                entity.getNotes(),
                entity.getVersion());
    }

    @Override
    public Optional<Person> find(final PersonId id) {
        return this.repository.findById(id.toString()).map(PersonPersistenceAdapter::toDomain);
    }

    @Override
    public List<Person> find(
            final @Nullable String search, final @Nullable PagePosition after, final int limit) {
        final Keyset keyset = new Keyset().after(after, "p.sortName", "p.id");
        if (search != null) {
            keyset.where(
                    "exists (select 1 from PersonJpaEntity named join named.names n"
                            + " where named = p and n.searchText like :search"
                            + Keyset.escape()
                            + ")",
                    Map.of("search", Keyset.containing(search)));
        }
        final TypedQuery<PersonJpaEntity> query =
                this.entityManager.createQuery(
                        "select p from PersonJpaEntity p"
                                + keyset.whereClause()
                                + " order by p.sortName, p.id",
                        PersonJpaEntity.class);
        keyset.parameters().forEach(query::setParameter);
        return query.setMaxResults(limit).getResultList().stream()
                .map(PersonPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Person save(final Person person) {
        final String id = person.id().toString();
        final PersonJpaEntity entity =
                this.repository
                        .findById(id)
                        .map(existing -> {
                            checkVersion(person, existing.getVersion());
                            return existing;
                        })
                        .orElseGet(() -> {
                            checkVersion(person, 0L);
                            return new PersonJpaEntity(id);
                        });
        entity.setSex(person.sex().key());
        entity.setNotes(person.notes());
        entity.setSortName(person.sortName());
        entity.setNames(person.names().stream().map(PersonPersistenceAdapter::row).toList());
        return toDomain(this.repository.saveAndFlush(entity));
    }

    @Override
    public void delete(final Person person) {
        final PersonJpaEntity entity =
                this.repository
                        .findById(person.id().toString())
                        .orElseThrow(() ->
                                new ConcurrentModificationException(
                                        "Person", person.id(), person.version(), -1));
        checkVersion(person, entity.getVersion());
        this.repository.delete(entity);
        this.repository.flush();
    }
}
