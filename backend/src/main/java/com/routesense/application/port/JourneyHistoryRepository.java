package com.routesense.application.port;

import com.routesense.domain.model.JourneyRecord;
import java.util.List;

// This is the port interface for the Journey History Repository, defining the operations that can be performed on journey records.

public interface JourneyHistoryRepository {
    void save(JourneyRecord record);
    List<JourneyRecord> findByUserId(String userId);
}
