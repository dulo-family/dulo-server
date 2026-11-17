package com.dulo.adapter.persistence.jpa.person;

import org.springframework.data.jpa.repository.JpaRepository;

interface PersonJpaRepository extends JpaRepository<PersonJpaEntity, String> {}
