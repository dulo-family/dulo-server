package com.dulo.core.testing;

import com.dulo.core.person.port.out.PersonRepository;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonName;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.PagePosition;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps people in a map, sorted, searched and versioned like the real adapter. */
public final class InMemoryPeople implements PersonRepository {

    private final Map<PersonId, Person> people = new LinkedHashMap<>();

    @Override
    public Optional<Person> find(final PersonId id) {
        return Optional.ofNullable(this.people.get(id));
    }

    @Override
    public List<Person> find(
            final @Nullable String search, final @Nullable PagePosition after, final int limit) {
        return this.people.values().stream()
                .filter(p ->
                        search == null
                                || p.names().stream()
                                        .map(PersonName::searchText)
                                        .anyMatch(text -> text.contains(search)))
                .filter(p -> after == null || Pages.isAfter(p.sortName(), p.id().value(), after))
                .sorted(
                        Comparator.comparing(Person::sortName)
                                .thenComparing(p -> p.id().toString()))
                .limit(limit)
                .toList();
    }

    @Override
    public Person save(final Person person) {
        final long actual =
                Optional.ofNullable(this.people.get(person.id())).map(Person::version).orElse(0L);
        if (person.version() != actual) {
            throw new ConcurrentModificationException(
                    "Person", person.id(), person.version(), actual);
        }
        final boolean isNew = !this.people.containsKey(person.id());
        final Person stored =
                new Person(
                        person.id(),
                        person.names(),
                        person.sex(),
                        person.notes(),
                        isNew ? 0 : person.version() + 1);
        this.people.put(stored.id(), stored);
        return stored;
    }

    @Override
    public void delete(final Person person) {
        final Person stored = this.people.get(person.id());
        if (stored == null || stored.version() != person.version()) {
            throw new ConcurrentModificationException(
                    "Person",
                    person.id(),
                    person.version(),
                    stored == null ? -1 : stored.version());
        }
        this.people.remove(person.id());
    }
}
