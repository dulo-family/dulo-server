package com.dulo.adapter.persistence.jpa.person;

import com.dulo.core.person.port.out.PersonRepository;

import jakarta.persistence.EntityManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PersonPersistenceConfiguration {

    /**
     * @param entityManager the shared, transaction-bound entity manager
     */
    @Bean
    PersonRepository personRepository(
            final PersonJpaRepository repository, final EntityManager entityManager) {
        return new PersonPersistenceAdapter(repository, entityManager);
    }
}
