package com.dulo.adapter.persistence.jpa.event;

import org.springframework.data.jpa.repository.JpaRepository;

interface EventJpaRepository extends JpaRepository<EventJpaEntity, String> {}
