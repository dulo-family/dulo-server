package com.dulo.adapter.persistence.jpa.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, String> {

    Optional<UserJpaEntity> findByProviderIdAndExternalSubject(
            String providerId, String externalSubject);

    boolean existsByRootTrue();

    List<UserJpaEntity> findAllByOrderByIdAsc();

    /** Distinct, since a user can have a role both from claims and by hand. */
    List<UserJpaEntity> findDistinctByRolesRoleKeyOrderByIdAsc(String roleKey);
}
