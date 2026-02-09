package com.routesense.infrastructure.persistence;

import com.routesense.domain.model.JourneySearch;


/**
 * Maps between the domain model and the JPA entity.
 */
public class JourneySearchMapper {

    public static JourneySearch toDomain(JourneySearchEntity entity) {
        if (entity == null) {
            return null;
        }
        return new JourneySearch(
            entity.getId(),
            entity.getOrigin(),
            entity.getDestination(),
            entity.getCreatedAt()
        );
    }

    public static JourneySearchEntity toEntity(JourneySearch domain) {
        if (domain == null) {
            return null;
        }
        JourneySearchEntity entity = new JourneySearchEntity(
            domain.getOrigin(),
            domain.getDestination(),
            domain.getCreatedAt()
        );
        entity.setId(domain.getId());
        return entity;
    }
}
