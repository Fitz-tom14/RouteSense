package com.routesense.web.controller;

import com.routesense.application.usecase.SearchJourneyUseCase;
import com.routesense.domain.model.JourneyOption;
import com.routesense.domain.model.JourneySearchResult;
import com.routesense.domain.model.Stop;
import com.routesense.web.dto.JourneyOptionDto;
import com.routesense.web.dto.JourneySearchResponseDto;
import com.routesense.web.dto.SearchJourneyRequestDto;
import com.routesense.web.dto.StopDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for journey routing operations.
 */
@RestController
@RequestMapping("/api/journeys")
public class JourneyController {

    // Inject the use case to keep the controller thin and focused on HTTP handling.
    private final SearchJourneyUseCase searchJourneyUseCase;

    // Constructor injection of the use case.
    public JourneyController(SearchJourneyUseCase searchJourneyUseCase) {
        this.searchJourneyUseCase = searchJourneyUseCase;
    }

    // POST /api/journeys/search
    @PostMapping("/search")
    public JourneySearchResponseDto search(@RequestBody SearchJourneyRequestDto request) {
        JourneySearchResult result = searchJourneyUseCase.execute(
                request.getOriginStopId(),
                request.getOriginLat(),
                request.getOriginLon(),
                request.getDestinationStopId()
        );

        // Convert domain model to API response DTO.
        List<JourneyOptionDto> options = result.getOptions().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new JourneySearchResponseDto(options, result.getCarBaselineCo2Grams());
    }

    // Helper method to convert a JourneyOption domain model to a JourneyOptionDto for API response.
    private JourneyOptionDto toDto(JourneyOption option) {
        List<StopDto> stops = option.getStops().stream()
                .map(this::toStopDto)
                .collect(Collectors.toList());

        // Map the JourneyOption fields to the JourneyOptionDto fields.
        return new JourneyOptionDto(
                option.getType().name(),// Convert enum to string for API response.
                stops,
                option.getTotalDurationSeconds(),// Total duration in seconds for the entire journey option.
                option.getTransfers(),
                option.getCo2Grams(),
                option.getScore(),
                option.isRecommended(),
            option.getRecommendationReason(),
            option.getModeSummary()
        );
    }

    // Helper method to convert a Stop domain model to a StopDto for API response.
    private StopDto toStopDto(Stop stop) {
        return new StopDto(stop.getId(), stop.getName(), stop.getLatitude(), stop.getLongitude());
    }
}
