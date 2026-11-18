package com.dulo.domain.family;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.domain.person.PersonId;

import org.junit.jupiter.api.Test;

import java.util.List;

class FamilyTest {

    private final PersonId mother = PersonId.newId();
    private final PersonId father = PersonId.newId();
    private final PersonId child = PersonId.newId();

    @Test
    void holdsPartnersAndChildren() {
        final Family family =
                Family.create(
                        List.of(this.mother, this.father),
                        RelationshipType.MARRIED,
                        List.of(new Child(this.child, ChildRelation.BIRTH)));

        assertThat(family.members()).containsExactly(this.mother, this.father, this.child);
        final Family withoutFather = family.without(this.father);
        assertThat(withoutFather.partners()).containsExactly(this.mother);
        assertThat(withoutFather.without(this.child).children()).isEmpty();
        assertThat(withoutFather.id()).isEqualTo(family.id());
    }

    @Test
    void everyMemberAppearsOnce() {
        assertThatThrownBy(() ->
                        Family.create(
                                List.of(this.mother, this.mother),
                                RelationshipType.MARRIED,
                                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        Family.create(
                                List.of(this.mother),
                                RelationshipType.MARRIED,
                                List.of(new Child(this.mother, ChildRelation.BIRTH))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        Family.create(
                                List.of(this.mother, this.father, this.child),
                                RelationshipType.PARTNERS,
                                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(RelationshipType.CIVIL_UNION.key()).isEqualTo("civil-union");
        assertThat(ChildRelation.fromKey("Step")).contains(ChildRelation.STEP);
    }
}
