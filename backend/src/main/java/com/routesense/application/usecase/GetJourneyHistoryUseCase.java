package com.routesense.application.usecase;

import com.routesense.application.port.JourneyHistoryRepository;
import com.routesense.domain.model.JourneyRecord;
import org.springframework.stereotype.Service;

import java.util.List;

// This use case class is responsible for retrieving the journey history for a given user
//  It interacts with the JourneyHistoryRepository to fetch the data and return it to the caller.
@Service
public class GetJourneyHistoryUseCase {

    private final JourneyHistoryRepository repository;

    // Constructor injection of the JourneyHistoryRepository to allow for decoupling and easier testing
    public GetJourneyHistoryUseCase(JourneyHistoryRepository repository) {
        this.repository = repository;
    }

    // The execute method takes a userId as input and returns a list of JourneyRecord objects associated with that user
    public List<JourneyRecord> execute(String userId) {
        return repository.findByUserId(userId);
    }
}
