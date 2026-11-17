package com.dulo.adapter.persistence.jpa.place;

import com.dulo.adapter.persistence.jpa.shared.Keyset;
import com.dulo.core.place.port.out.PlaceRepository;
import com.dulo.domain.place.Coordinates;
import com.dulo.domain.place.Place;
import com.dulo.domain.place.PlaceId;
import com.dulo.domain.place.PlaceType;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.PagePosition;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PlacePersistenceAdapter implements PlaceRepository {

    private final PlaceJpaRepository repository;
    private final EntityManager entityManager;

    PlacePersistenceAdapter(
            final PlaceJpaRepository repository, final EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    private static void checkVersion(final Place place, final long actual) {
        if (place.version() != actual) {
            throw new ConcurrentModificationException("Place", place.id(), place.version(), actual);
        }
    }

    private static Place toDomain(final PlaceJpaEntity entity) {
        final Double latitude = entity.getLatitude();
        final Double longitude = entity.getLongitude();
        final String enclosedBy = entity.getEnclosedBy();
        return new Place(
                PlaceId.parse(entity.getId()),
                entity.getName(),
                PlaceType.fromKey(entity.getType())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Unknown place type '%s'".formatted(entity.getType()))),
                latitude == null || longitude == null ? null : new Coordinates(latitude, longitude),
                enclosedBy == null ? null : PlaceId.parse(enclosedBy),
                entity.getVersion());
    }

    @Override
    public Optional<Place> find(final PlaceId id) {
        return this.repository.findById(id.toString()).map(PlacePersistenceAdapter::toDomain);
    }

    @Override
    public List<Place> find(
            final @Nullable String search, final @Nullable PagePosition after, final int limit) {
        final Keyset keyset = new Keyset().after(after, "p.sortName", "p.id");
        if (search != null) {
            keyset.where(
                    "p.sortName like :search" + Keyset.escape(),
                    Map.of("search", Keyset.containing(search)));
        }
        final TypedQuery<PlaceJpaEntity> query =
                this.entityManager.createQuery(
                        "select p from PlaceJpaEntity p"
                                + keyset.whereClause()
                                + " order by p.sortName, p.id",
                        PlaceJpaEntity.class);
        keyset.parameters().forEach(query::setParameter);
        return query.setMaxResults(limit).getResultList().stream()
                .map(PlacePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Place> findEnclosedBy(final PlaceId id) {
        return this.repository.findAllByEnclosedByOrderBySortNameAscIdAsc(id.toString()).stream()
                .map(PlacePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Place save(final Place place) {
        final String id = place.id().toString();
        final PlaceJpaEntity entity =
                this.repository
                        .findById(id)
                        .map(existing -> {
                            checkVersion(place, existing.getVersion());
                            return existing;
                        })
                        .orElseGet(() -> {
                            checkVersion(place, 0L);
                            return new PlaceJpaEntity(id);
                        });
        final Coordinates coordinates = place.coordinates();
        final PlaceId enclosedBy = place.enclosedBy();
        entity.setName(place.name());
        entity.setSortName(place.sortName());
        entity.setType(place.type().key());
        entity.setCoordinates(
                coordinates == null ? null : coordinates.latitude(),
                coordinates == null ? null : coordinates.longitude());
        entity.setEnclosedBy(enclosedBy == null ? null : enclosedBy.toString());
        return toDomain(this.repository.saveAndFlush(entity));
    }

    @Override
    public void delete(final Place place) {
        final PlaceJpaEntity entity =
                this.repository
                        .findById(place.id().toString())
                        .orElseThrow(() ->
                                new ConcurrentModificationException(
                                        "Place", place.id(), place.version(), -1));
        checkVersion(place, entity.getVersion());
        this.repository.delete(entity);
        this.repository.flush();
    }
}
