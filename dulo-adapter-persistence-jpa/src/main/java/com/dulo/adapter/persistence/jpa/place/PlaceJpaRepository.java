package com.dulo.adapter.persistence.jpa.place;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PlaceJpaRepository extends JpaRepository<PlaceJpaEntity, String> {

    List<PlaceJpaEntity> findAllByEnclosedByOrderBySortNameAscIdAsc(String enclosedBy);
}
