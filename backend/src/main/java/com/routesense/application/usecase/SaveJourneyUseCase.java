package com.routesense.application.usecase;

import com.routesense.application.port.JourneyHistoryRepository;
import com.routesense.domain.model.JourneyRecord;
import org.springframework.stereotype.Service;

// This use case class is responsible for saving a journey record to the repository.
//  It interacts with the JourneyHistoryRepository to persist the data.
@Service
public class SaveJourneyUseCase {

    private final JourneyHistoryRepository repository;

    // Constructor injection of the JourneyHistoryRepository to allow for decoupling and easier testing
    public SaveJourneyUseCase(JourneyHistoryRepository repository) {
        this.repository = repository;
    }

    // The execute method takes a JourneyRecord object as input and saves it to the repository
    public void execute(JourneyRecord record) {
        repository.save(record);
    }
}
