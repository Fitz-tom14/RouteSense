package com.routesense.web.controller;

import com.routesense.application.usecase.SearchJourneyUseCase;
import com.routesense.domain.model.JourneyOption;
import com.routesense.domain.model.Stop;
import com.routesense.web.dto.JourneyOptionDto;
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

    private final SearchJourneyUseCase searchJourneyUseCase;

    public JourneyController(SearchJourneyUseCase searchJourneyUseCase) {
        this.searchJourneyUseCase = searchJourneyUseCase;
    }

    @PostMapping("/search")
    public List<JourneyOptionDto> search(@RequestBody SearchJourneyRequestDto request) {
        List<JourneyOption> options = searchJourneyUseCase.execute(
                request.getOriginStopId(),
                request.getDestinationStopId()
        );

        return options.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private JourneyOptionDto toDto(JourneyOption option) {
        List<StopDto> stops = option.getStops().stream()
                .map(this::toStopDto)
                .collect(Collectors.toList());

        return new JourneyOptionDto(
                stops,
                option.getTotalDurationSeconds(),
                option.getTransfers(),
                option.isRecommended()
        );
    }

    private StopDto toStopDto(Stop stop) {
        return new StopDto(stop.getId(), stop.getName(), stop.getLatitude(), stop.getLongitude());
    }
}
