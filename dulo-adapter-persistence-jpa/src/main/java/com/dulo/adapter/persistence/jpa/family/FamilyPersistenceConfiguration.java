package com.dulo.adapter.persistence.jpa.family;

import com.dulo.core.family.port.out.FamilyRepository;

import jakarta.persistence.EntityManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FamilyPersistenceConfiguration {

    /**
     * @param entityManager the shared, transaction-bound entity manager
     */
    @Bean
    FamilyRepository familyRepository(
            final FamilyJpaRepository repository, final EntityManager entityManager) {
        return new FamilyPersistenceAdapter(repository, entityManager);
    }
}
