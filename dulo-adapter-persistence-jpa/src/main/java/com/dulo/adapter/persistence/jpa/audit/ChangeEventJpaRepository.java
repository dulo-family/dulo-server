package com.dulo.adapter.persistence.jpa.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ChangeEventJpaRepository extends JpaRepository<ChangeEventJpaEntity, Long> {

    List<ChangeEventJpaEntity> findAllByEntityTypeAndEntityIdOrderBySequenceAsc(
            String entityType, String entityId);
}
