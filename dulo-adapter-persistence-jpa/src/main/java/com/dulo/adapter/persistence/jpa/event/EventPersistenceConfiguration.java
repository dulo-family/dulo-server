package com.dulo.adapter.persistence.jpa.event;

import com.dulo.core.event.port.out.EventRepository;

import jakarta.persistence.EntityManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EventPersistenceConfiguration {

    /**
     * @param entityManager the shared, transaction-bound entity manager
     */
    @Bean
    EventRepository eventRepository(
            final EventJpaRepository repository, final EntityManager entityManager) {
        return new EventPersistenceAdapter(repository, entityManager);
    }
}
