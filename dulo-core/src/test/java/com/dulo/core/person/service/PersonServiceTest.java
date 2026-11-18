package com.dulo.core.person.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.event.port.in.ManageEventsUseCase.EventContent;
import com.dulo.core.family.port.in.ManageFamiliesUseCase.FamilyContent;
import com.dulo.core.person.port.in.ManagePeopleUseCase.PersonContent;
import com.dulo.core.shared.port.in.HasLinksException;
import com.dulo.core.testing.Genealogy;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeKind;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.FieldChange;
import com.dulo.domain.audit.Link;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventType;
import com.dulo.domain.event.Participant;
import com.dulo.domain.event.ParticipantRole;
import com.dulo.domain.family.Child;
import com.dulo.domain.family.ChildRelation;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.RelationshipType;
import com.dulo.domain.person.NameType;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonName;
import com.dulo.domain.person.PersonSort;
import com.dulo.domain.person.Sex;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.UserId;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class PersonServiceTest {

    private final Genealogy tree = new Genealogy();
    private final PersonService service = this.tree.personService();
    private final UserId editor = this.tree.user(DefaultRoles.EDITOR);

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

        assertThat(this.tree.people().find(anna.id())).isEmpty();
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

        final CursorPage<Person> first =
                this.service.people(this.editor, null, PersonSort.GIVEN, null, 3);
        final CursorPage<Person> second =
                this.service.people(this.editor, null, PersonSort.GIVEN, first.next(), 3);

        assertThat(first.items())
                .extracting(p -> p.sortName(PersonSort.GIVEN))
                .containsExactly("anna, müller", "eva, weber", "hans, weber");
        assertThat(second.items())
                .extracting(p -> p.sortName(PersonSort.GIVEN))
                .containsExactly("karl, müller");
        assertThat(second.next()).isNull();
        assertThat(this.service.people(this.editor, "FISCH", PersonSort.GIVEN, null, 50).items())
                .extracting(p -> p.sortName(PersonSort.GIVEN))
                .containsExactly("eva, weber");
    }

    @Test
    void checksVersionsAccessAndExistence() {
        final Person hans = this.service.createPerson(this.editor, named("Hans", "Weber"));
        final UserId viewer = this.tree.user(DefaultRoles.VIEWER);

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
        assertThatThrownBy(() -> this.service.people(this.editor, null, PersonSort.GIVEN, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDisabledFeatureIsDeniedToEveryone() {
        this.tree
                .trees()
                .save(
                        new TreeSettings(
                                this.tree.trees().find().orElseThrow().id(),
                                "Smiths",
                                null,
                                Set.of(),
                                this.tree.trees().find().orElseThrow().version()));

        assertThatThrownBy(() -> this.service.people(this.editor, null, PersonSort.GIVEN, null, 50))
                .isInstanceOfSatisfying(
                        AccessDeniedException.class,
                        e ->
                                assertThat(e.reason())
                                        .isEqualTo(AccessDeniedException.Reason.FEATURE_DISABLED));
    }

    @Test
    void sortsBySurnameOnRequest() {
        this.service.createPerson(this.editor, named("Hans", "Weber"));
        this.service.createPerson(this.editor, named("Anna", "Zett"));
        this.service.createPerson(this.editor, named("Karl", "Abel"));

        final CursorPage<Person> first =
                this.service.people(this.editor, null, PersonSort.SURNAME, null, 2);

        assertThat(first.items())
                .extracting(p -> p.sortName(PersonSort.SURNAME))
                .containsExactly("abel, karl", "weber, hans");
        assertThat(
                        this.service
                                .people(this.editor, null, PersonSort.SURNAME, first.next(), 2)
                                .items())
                .extracting(p -> p.sortName(PersonSort.SURNAME))
                .containsExactly("zett, anna");
    }

    @Test
    void deletingAPersonInFamiliesAndEventsNeedsConfirmation() {
        final Person mother = this.service.createPerson(this.editor, named("Anna", "Weber"));
        final Person child = this.service.createPerson(this.editor, named("Karl", "Weber"));
        final Family family =
                this.tree
                        .familyService()
                        .createFamily(
                                this.editor,
                                new FamilyContent(
                                        List.of(mother.id()),
                                        RelationshipType.UNKNOWN,
                                        List.of(new Child(child.id(), ChildRelation.BIRTH))));
        final Event birth =
                this.tree
                        .eventService()
                        .createEvent(
                                this.editor,
                                new EventContent(
                                        EventType.BIRTH,
                                        null,
                                        null,
                                        null,
                                        family.id(),
                                        null,
                                        List.of(
                                                new Participant(
                                                        child.id(), ParticipantRole.PRINCIPAL),
                                                new Participant(
                                                        mother.id(), ParticipantRole.OTHER))));
        final List<Link> links =
                List.of(
                        new Link(EntityType.FAMILY, family.id().toString(), "partners"),
                        new Link(EntityType.EVENT, birth.id().toString(), "participants"));

        assertThatThrownBy(() ->
                        this.service.deletePerson(
                                this.editor, mother.id(), mother.version(), false))
                .isInstanceOfSatisfying(
                        HasLinksException.class, e -> assertThat(e.links()).isEqualTo(links));
        this.service.deletePerson(this.editor, mother.id(), mother.version(), true);

        assertThat(this.tree.families().find(family.id()).orElseThrow().partners()).isEmpty();
        assertThat(this.tree.events().find(birth.id()).orElseThrow().participants())
                .extracting(Participant::person)
                .containsExactly(child.id());
        assertThat(this.service.personHistory(this.editor, mother.id()).getLast().removedLinks())
                .isEqualTo(links);
        // A child's link is to the family's children.
        assertThatThrownBy(() ->
                        this.service.deletePerson(this.editor, child.id(), child.version(), false))
                .isInstanceOfSatisfying(
                        HasLinksException.class,
                        e ->
                                assertThat(e.links())
                                        .extracting(Link::field)
                                        .containsExactly("children", "participants"));
    }
}
