package com.dulo.domain.family;

import com.dulo.domain.person.PersonId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Partners and their children. A family may have one partner only, when the other is unknown, or
 * none, for siblings whose parents are unknown.
 *
 * @param partners up to two different people
 * @param children in order of birth, as far as known; none of them a partner
 * @param version optimistic-locking version, 0 for a family that has not been stored yet
 */
public record Family(
        FamilyId id,
        List<PersonId> partners,
        RelationshipType relationshipType,
        List<Child> children,
        long version) {

    public static final int MAX_PARTNERS = 2;
    public static final int MAX_CHILDREN = 100;

    public Family {
        Objects.requireNonNull(id, "id");
        partners = List.copyOf(partners);
        Objects.requireNonNull(relationshipType, "relationshipType");
        children = List.copyOf(children);
        if (partners.size() > MAX_PARTNERS) {
            throw new IllegalArgumentException(
                    "A family has at most %d partners".formatted(MAX_PARTNERS));
        }
        if (children.size() > MAX_CHILDREN) {
            throw new IllegalArgumentException(
                    "A family has at most %d children".formatted(MAX_CHILDREN));
        }
        final Set<PersonId> members = new HashSet<>();
        for (final PersonId partner : partners) {
            if (!members.add(partner)) {
                throw new IllegalArgumentException("A person is a partner of a family only once");
            }
        }
        for (final Child child : children) {
            if (!members.add(child.person())) {
                throw new IllegalArgumentException(
                        "A person is either a partner or a child of a family, and only once");
            }
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /** A new family, not stored yet. */
    public static Family create(
            final List<PersonId> partners,
            final RelationshipType relationshipType,
            final List<Child> children) {
        return new Family(FamilyId.newId(), partners, relationshipType, children, 0);
    }

    /** The partners and children, partners first. */
    public List<PersonId> members() {
        return Stream.concat(partners.stream(), children.stream().map(Child::person)).toList();
    }

    /**
     * The family with another partner.
     *
     * @throws IllegalArgumentException if the family has two partners already, or the person is a
     *     member
     */
    public Family withPartner(final PersonId partner) {
        return new Family(
                id,
                Stream.concat(partners.stream(), Stream.of(partner)).toList(),
                relationshipType,
                children,
                version);
    }

    /**
     * The family with another child, last.
     *
     * @throws IllegalArgumentException if the family has the most children already, or the person
     *     is a member
     */
    public Family withChild(final Child child) {
        return new Family(
                id,
                partners,
                relationshipType,
                Stream.concat(children.stream(), Stream.of(child)).toList(),
                version);
    }

    /** The family without the person, as partner or as child. */
    public Family without(final PersonId person) {
        return new Family(
                id,
                partners.stream().filter(p -> !p.equals(person)).toList(),
                relationshipType,
                children.stream().filter(c -> !c.person().equals(person)).toList(),
                version);
    }
}
