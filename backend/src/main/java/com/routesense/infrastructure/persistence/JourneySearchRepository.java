package com.routesense.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for JPA persistence of JourneySearchEntity.
 * Works only with the entity, not the domain model.
 */
@Repository
public interface JourneySearchRepository extends JpaRepository<JourneySearchEntity, Long> {
}
