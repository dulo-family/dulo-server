package com.dulo.core.person.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.person.port.in.ManagePeopleUseCase;
import com.dulo.core.person.port.out.PersonRepository;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.shared.service.Cursors;
import com.dulo.core.shared.service.Searches;
import com.dulo.domain.access.Access;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.shared.PagePosition;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.List;

public class PersonService implements ManagePeopleUseCase {

    private final PersonRepository people;
    private final ChangeEventRepository events;
    private final AccessPolicy accessPolicy;
    private final ChangeRecorder recorder;
    private final UnitOfWork unitOfWork;

    public PersonService(
            final PersonRepository people,
            final ChangeEventRepository events,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        this.people = people;
        this.events = events;
        this.accessPolicy = accessPolicy;
        this.recorder = recorder;
        this.unitOfWork = unitOfWork;
    }

    private static void checkVersion(final Person person, final long expectedVersion) {
        if (person.version() != expectedVersion) {
            throw new ConcurrentModificationException(
                    "Person", person.id(), expectedVersion, person.version());
        }
    }

    private static PagePosition position(final Person person) {
        return new PagePosition(person.sortName(), person.id().value());
    }

    @Override
    public CursorPage<Person> people(
            final UserId caller,
            final @Nullable String search,
            final @Nullable String after,
            final int limit) {
        CursorPage.checkLimit(limit);
        final PagePosition position = Cursors.decodePosition(after);
        final String normalized = Searches.normalize(search);
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.VIEW);
            return Cursors.page(
                    this.people.find(normalized, position, limit + 1),
                    limit,
                    PersonService::position);
        });
    }

    @Override
    public Person person(final UserId caller, final PersonId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.VIEW);
            return this.find(id);
        });
    }

    @Override
    public Person createPerson(final UserId caller, final PersonContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.CREATE);
            final Person stored =
                    this.people.save(
                            Person.create(content.names(), content.sex(), content.notes()));
            this.recorder.changeSet(caller).created(PersonFields.INSTANCE, stored);
            return stored;
        });
    }

    @Override
    public Person replacePerson(
            final UserId caller,
            final PersonId id,
            final long expectedVersion,
            final PersonContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.EDIT);
            final Person existing = this.find(id);
            checkVersion(existing, expectedVersion);
            final Person stored =
                    this.people.save(
                            new Person(
                                    id,
                                    content.names(),
                                    content.sex(),
                                    content.notes(),
                                    existing.version()));
            this.recorder.changeSet(caller).changed(PersonFields.INSTANCE, existing, stored);
            return stored;
        });
    }

    /** Nothing refers to people yet; families and events will. */
    @Override
    public void deletePerson(
            final UserId caller,
            final PersonId id,
            final long expectedVersion,
            final boolean confirmed) {
        this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.DELETE);
            final Person person = this.find(id);
            checkVersion(person, expectedVersion);
            this.people.delete(person);
            this.recorder.changeSet(caller).deleted(PersonFields.INSTANCE, person, List.of());
            return person;
        });
    }

    @Override
    public List<ChangeEvent> personHistory(final UserId caller, final PersonId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.VIEW);
            final List<ChangeEvent> history = this.events.history(EntityType.PERSON, id.toString());
            if (history.isEmpty()) {
                throw new NotFoundException("Person", id);
            }
            return history;
        });
    }

    private Person find(final PersonId id) {
        return this.people.find(id).orElseThrow(() -> new NotFoundException("Person", id));
    }
}
