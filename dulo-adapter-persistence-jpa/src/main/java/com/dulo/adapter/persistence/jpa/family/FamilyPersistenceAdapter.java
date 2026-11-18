package com.dulo.adapter.persistence.jpa.family;

import com.dulo.adapter.persistence.jpa.shared.Keyset;
import com.dulo.core.family.port.out.FamilyRepository;
import com.dulo.domain.family.Child;
import com.dulo.domain.family.ChildRelation;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.family.RelationshipType;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.PagePosition;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FamilyPersistenceAdapter implements FamilyRepository {

    private final FamilyJpaRepository repository;
    private final EntityManager entityManager;

    FamilyPersistenceAdapter(
            final FamilyJpaRepository repository, final EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    private static void checkVersion(final Family family, final long actual) {
        if (family.version() != actual) {
            throw new ConcurrentModificationException(
                    "Family", family.id(), family.version(), actual);
        }
    }

    private static Child toDomain(final FamilyChildRow row) {
        return new Child(
                PersonId.parse(row.personId()),
                ChildRelation.fromKey(row.relation())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Unknown child relation '%s'".formatted(row.relation()))));
    }

    private static Family toDomain(final FamilyJpaEntity entity) {
        return new Family(
                FamilyId.parse(entity.getId()),
                entity.getPartners().stream().map(PersonId::parse).toList(),
                RelationshipType.fromKey(entity.getRelationshipType())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Unknown relationship type '%s'"
                                                .formatted(entity.getRelationshipType()))),
                entity.getChildren().stream().map(FamilyPersistenceAdapter::toDomain).toList(),
                entity.getVersion());
    }

    @Override
    public Optional<Family> find(final FamilyId id) {
        return this.repository.findById(id.toString()).map(FamilyPersistenceAdapter::toDomain);
    }

    @Override
    public List<Family> find(
            final @Nullable PersonId member, final @Nullable PagePosition after, final int limit) {
        // The sort key of a family is its id.
        final Keyset keyset = new Keyset().after(after, "f.id", "f.id");
        if (member != null) {
            keyset.where(
                    "(exists (select 1 from FamilyJpaEntity withPartner join withPartner.partners"
                            + " partner where withPartner = f and partner = :member)"
                            + " or exists (select 1 from FamilyJpaEntity withChild join"
                            + " withChild.children child where withChild = f and child.personId"
                            + " = :member))",
                    Map.of("member", member.toString()));
        }
        final TypedQuery<FamilyJpaEntity> query =
                this.entityManager.createQuery(
                        "select f from FamilyJpaEntity f" + keyset.whereClause() + " order by f.id",
                        FamilyJpaEntity.class);
        keyset.parameters().forEach(query::setParameter);
        return query.setMaxResults(limit).getResultList().stream()
                .map(FamilyPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Family> findWithMember(final PersonId member) {
        return this.find(member, null, Integer.MAX_VALUE);
    }

    @Override
    public Family save(final Family family) {
        final String id = family.id().toString();
        final FamilyJpaEntity entity =
                this.repository
                        .findById(id)
                        .map(existing -> {
                            checkVersion(family, existing.getVersion());
                            return existing;
                        })
                        .orElseGet(() -> {
                            checkVersion(family, 0L);
                            return new FamilyJpaEntity(id);
                        });
        entity.setRelationshipType(family.relationshipType().key());
        entity.setPartners(family.partners().stream().map(PersonId::toString).toList());
        entity.setChildren(
                family.children().stream()
                        .map(child ->
                                new FamilyChildRow(
                                        child.person().toString(), child.relation().key()))
                        .toList());
        return toDomain(this.repository.saveAndFlush(entity));
    }

    @Override
    public void delete(final Family family) {
        final FamilyJpaEntity entity =
                this.repository
                        .findById(family.id().toString())
                        .orElseThrow(() ->
                                new ConcurrentModificationException(
                                        "Family", family.id(), family.version(), -1));
        checkVersion(family, entity.getVersion());
        this.repository.delete(entity);
        this.repository.flush();
    }
}
