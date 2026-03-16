package com.routesense.infrastructure.persistence;

import com.routesense.application.port.JourneyHistoryRepository;
import com.routesense.domain.model.JourneyRecord;
import org.springframework.stereotype.Component;

import java.util.List;

// This class is an adapter that implements the JourneyHistoryRepository interface using JPA for persistence.
// It uses a Spring Data repository (SpringDataJourneyRepository) to perform the actual database operations
@Component
public class JpaJourneyHistoryAdapter implements JourneyHistoryRepository {

    private final SpringDataJourneyRepository repo;

    // Constructor injection of the SpringDataJourneyRepository to allow for decoupling and easier testing
    public JpaJourneyHistoryAdapter(SpringDataJourneyRepository repo) {
        this.repo = repo;
    }

    // The save method takes a JourneyRecord object and saves it to the database using the Spring Data repository
    @Override
    public void save(JourneyRecord record) {
        repo.save(record);
    }

    // The findByUserId method retrieves a list of JourneyRecord objects associated with a given userId, ordered by timestamp in descending order
    @Override
    public List<JourneyRecord> findByUserId(String userId) {
        return repo.findByUserIdOrderByTimestampDesc(userId);
    }
}
