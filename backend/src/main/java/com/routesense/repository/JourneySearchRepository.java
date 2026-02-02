package com.routesense.repository;

import com.routesense.domain.JourneySearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JourneySearchRepository extends JpaRepository<JourneySearch, Long> {
}
