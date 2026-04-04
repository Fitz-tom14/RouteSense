package com.routesense.application.port;

import com.routesense.domain.model.JourneyRecord;
import java.util.List;

// Port interface — the application layer defines what it needs here, and the infrastructure provides the actual JPA implementation.
// This keeps the domain code independent of any database framework.
public interface JourneyHistoryRepository {
    void save(JourneyRecord record);                     // persists a newly completed journey to the DB
    List<JourneyRecord> findByUserId(String userId);     // fetches all past journeys for a specific user
}
