package com.routesense.infrastructure.persistence;

import com.routesense.domain.model.JourneyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataJourneyRepository extends JpaRepository<JourneyRecord, Long> {
    List<JourneyRecord> findByUserIdOrderByTimestampDesc(String userId);
}
