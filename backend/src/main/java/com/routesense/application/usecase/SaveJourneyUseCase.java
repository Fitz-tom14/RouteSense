package com.routesense.application.usecase;

import com.routesense.application.port.JourneyHistoryRepository;
import com.routesense.domain.model.JourneyRecord;
import org.springframework.stereotype.Service;// This use case class is responsible for saving a completed journey record to the user's journey history.

// Application layer — talks to the port interface only, not JPA directly, so the domain stays clean.
@Service
public class SaveJourneyUseCase {

    private final JourneyHistoryRepository repository;

    // Spring sees this constructor and automatically injects the repository — no manual wiring needed
    public SaveJourneyUseCase(JourneyHistoryRepository repository) {
        this.repository = repository;
    }

    // Called by the history controller once the user's journey is complete
    public void execute(JourneyRecord record) {
        repository.save(record); // hands off to the JPA implementation in the infrastructure layer
    }
}
