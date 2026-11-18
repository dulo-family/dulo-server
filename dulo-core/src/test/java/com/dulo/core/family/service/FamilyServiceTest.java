package com.dulo.core.family.service;

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
import com.dulo.domain.family.Child;
import com.dulo.domain.family.ChildRelation;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.family.RelationshipType;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.Sex;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.user.UserId;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class FamilyServiceTest {

    private final Genealogy tree = new Genealogy();
    private final FamilyService service = this.tree.familyService();
    private final UserId editor = this.tree.user(DefaultRoles.EDITOR);
    private final PersonId mother = this.person();
    private final PersonId father = this.person();
    private final PersonId child = this.person();

    private PersonId person() {
        return this.tree
                .personService()
                .createPerson(this.editor, new PersonContent(List.of(), Sex.UNKNOWN, null))
                .id();
    }

    private FamilyContent parentsWithChild() {
        return new FamilyContent(
                List.of(this.mother, this.father),
                RelationshipType.MARRIED,
                List.of(new Child(this.child, ChildRelation.BIRTH)));
    }

    @Test
    void createsReadsReplacesAndListsFamilies() {
        final Family family = this.service.createFamily(this.editor, this.parentsWithChild());
        final Family other =
                this.service.createFamily(
                        this.editor,
                        new FamilyContent(
                                List.of(this.father), RelationshipType.UNKNOWN, List.of()));

        assertThat(this.service.family(this.editor, family.id())).isEqualTo(family);
        final Family changed =
                this.service.replaceFamily(
                        this.editor,
                        family.id(),
                        family.version(),
                        new FamilyContent(
                                List.of(this.mother),
                                RelationshipType.PARTNERS,
                                List.of(new Child(this.child, ChildRelation.ADOPTED))));
        assertThat(changed.version()).isEqualTo(1);
        assertThat(this.service.familyHistory(this.editor, family.id()).getLast().changes())
                .containsExactly(
                        new FieldChange(
                                "partners",
                                List.of(this.mother.toString(), this.father.toString()),
                                List.of(this.mother.toString())),
                        new FieldChange("relationshipType", "married", "partners"),
                        new FieldChange(
                                "children",
                                List.of(
                                        Map.of(
                                                "person",
                                                this.child.toString(),
                                                "relation",
                                                "birth")),
                                List.of(
                                        Map.of(
                                                "person",
                                                this.child.toString(),
                                                "relation",
                                                "adopted"))));

        // Ids order families only to the millisecond, so either may come first.
        final CursorPage<Family> first = this.service.families(this.editor, null, null, 1);
        final CursorPage<Family> second = this.service.families(this.editor, null, first.next(), 1);
        assertThat(List.of(first.items().getFirst().id(), second.items().getFirst().id()))
                .containsExactlyInAnyOrder(family.id(), other.id());
        assertThat(second.next()).isNull();
        assertThat(this.service.families(this.editor, this.mother, null, 50).items())
                .extracting(Family::id)
                .containsExactly(family.id());
    }

    @Test
    void membersMustExist() {
        assertThatThrownBy(() ->
                        this.service.createFamily(
                                this.editor,
                                new FamilyContent(
                                        List.of(PersonId.newId()),
                                        RelationshipType.MARRIED,
                                        List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("person");
    }

    @Test
    void deletingAFamilyWithEventsNeedsConfirmation() {
        final Family family = this.service.createFamily(this.editor, this.parentsWithChild());
        final Event wedding =
                this.tree
                        .eventService()
                        .createEvent(
                                this.editor,
                                new EventContent(
                                        EventType.MARRIAGE,
                                        null,
                                        null,
                                        null,
                                        family.id(),
                                        null,
                                        List.of()));
        final List<Link> links =
                List.of(new Link(EntityType.EVENT, wedding.id().toString(), "family"));

        assertThatThrownBy(() ->
                        this.service.deleteFamily(
                                this.editor, family.id(), family.version(), false))
                .isInstanceOfSatisfying(
                        HasLinksException.class, e -> assertThat(e.links()).isEqualTo(links));
        this.service.deleteFamily(this.editor, family.id(), family.version(), true);

        assertThat(this.tree.events().find(wedding.id()).orElseThrow().family()).isNull();
        // The people stay.
        assertThat(this.tree.people().find(this.mother)).isPresent();
        assertThat(this.service.familyHistory(this.editor, family.id()))
                .extracting(ChangeEvent::kind)
                .containsExactly(ChangeKind.CREATED, ChangeKind.DELETED);
        assertThatThrownBy(() -> this.service.family(this.editor, family.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void checksVersionsAccessAndExistence() {
        final Family family = this.service.createFamily(this.editor, this.parentsWithChild());
        final UserId viewer = this.tree.user(DefaultRoles.VIEWER);

        assertThatThrownBy(() -> this.service.createFamily(viewer, this.parentsWithChild()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                        this.service.replaceFamily(
                                this.editor, family.id(), 5, this.parentsWithChild()))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() -> this.service.deleteFamily(this.editor, family.id(), 5, true))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() -> this.service.family(this.editor, FamilyId.newId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> this.service.familyHistory(this.editor, FamilyId.newId()))
                .isInstanceOf(NotFoundException.class);
    }
}
