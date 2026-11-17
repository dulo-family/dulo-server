package com.dulo.core.person.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.person.port.in.ManagePeopleUseCase.PersonContent;
import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryChangeEvents;
import com.dulo.core.testing.InMemoryPeople;
import com.dulo.core.testing.InMemoryRoles;
import com.dulo.core.testing.InMemoryTrees;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.person.NameType;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonName;
import com.dulo.domain.person.Sex;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class PersonServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryPeople people = new InMemoryPeople();
    private final InMemoryChangeEvents events = new InMemoryChangeEvents();
    private final InMemoryTrees trees =
            new InMemoryTrees(TreeSettings.create("Smiths", null, Set.of(Feature.PEOPLE)));
    private final PersonService service =
            new PersonService(
                    this.people,
                    this.events,
                    new AccessPolicy(
                            this.users,
                            new InMemoryRoles(DefaultRoles.all()),
                            this.trees,
                            FeatureOverrides::none),
                    this.events.recorder(),
                    new DirectUnitOfWork());
    private final UserId editor =
            this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.EDITOR)).id();

    private static PersonContent named(final String given, final String surname) {
        return new PersonContent(
                List.of(PersonName.of(NameType.BIRTH, given, surname)), Sex.UNKNOWN, null);
    }

    @Test
    void createsReadsReplacesAndDeletesPeople() {
        final Person anna =
                this.service.createPerson(
                        this.editor,
                        new PersonContent(
                                List.of(
                                        new PersonName(
                                                NameType.BIRTH,
                                                "Anna",
                                                "Müller",
                                                null,
                                                null,
                                                "Annie")),
                                Sex.FEMALE,
                                "Born on a farm"));
        assertThat(this.service.person(this.editor, anna.id())).isEqualTo(anna);

        final Person married =
                this.service.replacePerson(
                        this.editor,
                        anna.id(),
                        anna.version(),
                        new PersonContent(
                                List.of(
                                        PersonName.of(NameType.MARRIED, "Anna", "Schmidt"),
                                        anna.names().getFirst()),
                                Sex.FEMALE,
                                null));
        this.service.deletePerson(this.editor, anna.id(), married.version(), false);

        assertThat(this.people.find(anna.id())).isEmpty();
        final List<ChangeEvent> history = this.service.personHistory(this.editor, anna.id());
        assertThat(history)
                .extracting(ChangeEvent::kind)
                .containsExactly(ChangeKind.CREATED, ChangeKind.CHANGED, ChangeKind.DELETED);
        assertThat(history.getFirst().changes())
                .containsExactly(
                        new FieldChange(
                                "names",
                                null,
                                List.of(
                                        Map.of(
                                                "type", "birth",
                                                "given", "Anna",
                                                "surname", "Müller",
                                                "nickname", "Annie"))),
                        new FieldChange("sex", null, "female"),
                        new FieldChange("notes", null, "Born on a farm"));
        assertThat(history.get(1).changes())
                .extracting(FieldChange::field)
                .containsExactly("names", "notes");
    }

    @Test
    void pagesByGivenNameAndSearchesAllNames() {
        this.service.createPerson(this.editor, named("Hans", "Weber"));
        this.service.createPerson(this.editor, named("Anna", "Müller"));
        this.service.createPerson(this.editor, named("Karl", "Müller"));
        this.service.createPerson(
                this.editor,
                new PersonContent(
                        List.of(
                                PersonName.of(NameType.MARRIED, "Eva", "Weber"),
                                PersonName.of(NameType.BIRTH, "Eva", "Fischer")),
                        Sex.FEMALE,
                        null));

        final CursorPage<Person> first = this.service.people(this.editor, null, null, 3);
        final CursorPage<Person> second = this.service.people(this.editor, null, first.next(), 3);

        assertThat(first.items())
                .extracting(Person::sortName)
                .containsExactly("anna, müller", "eva, weber", "hans, weber");
        assertThat(second.items()).extracting(Person::sortName).containsExactly("karl, müller");
        assertThat(second.next()).isNull();
        assertThat(this.service.people(this.editor, "FISCH", null, 50).items())
                .extracting(Person::sortName)
                .containsExactly("eva, weber");
    }

    @Test
    void checksVersionsAccessAndExistence() {
        final Person hans = this.service.createPerson(this.editor, named("Hans", "Weber"));
        final UserId viewer =
                this.users.save(Users.user(UserStatus.ACTIVE, DefaultRoles.VIEWER)).id();

        assertThatThrownBy(() -> this.service.createPerson(viewer, named("No", "One")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.service.replacePerson(
                                this.editor, hans.id(), hans.version() + 1, named("X", "Y")))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() ->
                        this.service.deletePerson(this.editor, hans.id(), hans.version() + 1, true))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() -> this.service.person(this.editor, PersonId.newId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> this.service.personHistory(this.editor, PersonId.newId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> this.service.people(this.editor, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDisabledFeatureIsDeniedToEveryone() {
        this.trees.save(
                new TreeSettings(
                        this.trees.find().orElseThrow().id(),
                        "Smiths",
                        null,
                        Set.of(),
                        this.trees.find().orElseThrow().version()));

        assertThatThrownBy(() -> this.service.people(this.editor, null, null, 50))
                .isInstanceOfSatisfying(
                        AccessDeniedException.class,
                        e ->
                                assertThat(e.reason())
                                        .isEqualTo(AccessDeniedException.Reason.FEATURE_DISABLED));
    }
}
