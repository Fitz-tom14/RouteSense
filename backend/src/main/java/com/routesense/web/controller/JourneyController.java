package com.routesense.web.controller;

import com.routesense.application.usecase.SearchJourneyUseCase;
import com.routesense.domain.model.JourneyLeg;
import com.routesense.domain.model.JourneyOption;
import com.routesense.domain.model.JourneySearchResult;
import com.routesense.domain.model.Stop;
import com.routesense.web.dto.JourneyLegDto;
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

// Thin controller — takes a search request from the frontend, runs it through the use case, and maps the result to DTOs.
// toDto() handles the domain → DTO conversion so the domain model never leaks into the HTTP response.
@RestController
@RequestMapping("/api/journeys")
public class JourneyController {

    private final SearchJourneyUseCase searchJourneyUseCase;

    public JourneyController(SearchJourneyUseCase searchJourneyUseCase) {
        this.searchJourneyUseCase = searchJourneyUseCase;
    }

    // POST /api/journeys/search — @RequestBody tells Spring to parse the incoming JSON into a SearchJourneyRequestDto
    @PostMapping("/search")
    public JourneySearchResponseDto search(@RequestBody SearchJourneyRequestDto request) {
        JourneySearchResult result = searchJourneyUseCase.execute(
                request.getOriginStopId(),
                request.getOriginLat(),
                request.getOriginLon(),
                request.getDestinationStopId(),
                request.getDestinationLat(),
                request.getDestinationLon(),
                request.getDepartureTimeSeconds(),
                request.getArriveBySeconds()
        );

        // stream().map().collect() is Java's way of converting every item in a list from one type to another
        List<JourneyOptionDto> options = result.getOptions().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new JourneySearchResponseDto(options, result.getCarBaselineCo2Grams(), result.getCarRouteGeometry());
    }

    // Converts the internal domain object to a DTO — keeps the domain model out of the HTTP response
    private JourneyOptionDto toDto(JourneyOption option) {
        List<StopDto> stops = option.getStops().stream()
                .map(this::toStopDto)
                .collect(Collectors.toList());

        List<JourneyLegDto> legs = option.getLegs().stream()
                .map(this::toLegDto)
                .collect(Collectors.toList());

        return new JourneyOptionDto(
                option.getType().name(),
                stops,
                option.getTotalDurationSeconds(),
                option.getTransfers(),
                option.getCo2Grams(),
                option.getScore(),
                option.isRecommended(),
                option.getRecommendationReason(),
                option.getModeSummary(),
                legs
        );
    }

    private JourneyLegDto toLegDto(JourneyLeg leg) {
        return new JourneyLegDto(
                leg.getServiceName(),
                leg.getFromStopName(),
                leg.getToStopName(),
                leg.getDepartureTime(),
                leg.getArrivalTime(),
                leg.getMode(),
                leg.getShapePoints()
        );
    }

    private StopDto toStopDto(Stop stop) {
        return new StopDto(stop.getId(), stop.getName(), stop.getLatitude(), stop.getLongitude());
    }
}
