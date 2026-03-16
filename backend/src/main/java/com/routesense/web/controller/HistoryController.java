package com.routesense.web.controller;

import com.routesense.application.usecase.GetJourneyHistoryUseCase;
import com.routesense.application.usecase.SaveJourneyUseCase;
import com.routesense.domain.model.JourneyRecord;
import com.routesense.web.dto.JourneyRecordDto;
import com.routesense.web.dto.SaveJourneyRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// This controller class handles HTTP requests related to journey history.
// It provides endpoints for saving a new journey record and retrieving the journey history for a specific user.
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    // Dependencies on the use case classes for saving a journey and getting journey history
    private final SaveJourneyUseCase saveJourney;
    private final GetJourneyHistoryUseCase getHistory;

    // Constructor injection of the use case classes to allow for decoupling and easier testing
    public HistoryController(SaveJourneyUseCase saveJourney, GetJourneyHistoryUseCase getHistory) {
        this.saveJourney = saveJourney;
        this.getHistory = getHistory;
    }

    // Endpoint for saving a new journey record. It accepts a SaveJourneyRequestDto object in the request body, converts it to a JourneyRecord, and calls the save use case to persist it.
    @PostMapping
    public ResponseEntity<Void> save(@RequestBody SaveJourneyRequestDto dto) {
        JourneyRecord record = new JourneyRecord(
                dto.timestamp(),
                dto.date(),
                dto.durationSeconds(),
                dto.co2Grams(),
                dto.carCo2Grams(),
                dto.modeSummary(),
                dto.destination(),
                dto.transfers(),
                dto.userId()
        );
        saveJourney.execute(record);
        return ResponseEntity.ok().build();
    }

    // Endpoint for retrieving the journey history for a specific user. It accepts a userId as a query parameter, calls the getHistory use case to fetch the data, and returns a list of JourneyRecordDto objects in the response.
    @GetMapping
    public List<JourneyRecordDto> getHistory(@RequestParam String userId) {
        return getHistory.execute(userId).stream()
                .map(r -> new JourneyRecordDto(
                        r.getId(),
                        r.getTimestamp(),
                        r.getDate(),
                        r.getDurationSeconds(),
                        r.getCo2Grams(),
                        r.getCarCo2Grams(),
                        r.getModeSummary(),
                        r.getDestination(),
                        r.getTransfers()
                ))
                .toList();
    }
}
