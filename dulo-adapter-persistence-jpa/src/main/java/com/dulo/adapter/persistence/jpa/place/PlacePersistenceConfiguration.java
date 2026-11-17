package com.dulo.adapter.persistence.jpa.place;

import com.dulo.core.place.port.out.PlaceRepository;

import jakarta.persistence.EntityManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PlacePersistenceConfiguration {

    /**
     * @param entityManager the shared, transaction-bound entity manager
     */
    @Bean
    PlaceRepository placeRepository(
            final PlaceJpaRepository repository, final EntityManager entityManager) {
        return new PlacePersistenceAdapter(repository, entityManager);
    }
}
