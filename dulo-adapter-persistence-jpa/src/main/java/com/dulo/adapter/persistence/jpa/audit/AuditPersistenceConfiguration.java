package com.dulo.adapter.persistence.jpa.audit;

import com.dulo.core.audit.port.out.ChangeEventRepository;

import jakarta.persistence.EntityManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuditPersistenceConfiguration {

    /**
     * @param entityManager the shared, transaction-bound entity manager
     */
    @Bean
    ChangeEventRepository changeEventRepository(
            final ChangeEventJpaRepository repository, final EntityManager entityManager) {
        return new ChangeEventPersistenceAdapter(repository, entityManager);
    }
}
