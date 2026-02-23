



package com.routesense.web.controller;

import com.routesense.application.usecase.GetMapStopsUseCase;
import com.routesense.application.usecase.GetStopDeparturesUseCase;
import com.routesense.domain.model.Departure;
import com.routesense.domain.model.TransportMode;
import com.routesense.domain.model.TransportStop;
import com.routesense.web.dto.DepartureDto;
import com.routesense.web.dto.MapStopDto;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Thin controller for Map View endpoints.
 *
 * Responsibilities:
 * - Parse request params
 * - Call use cases
 * - Map domain models to DTOs
 */
@RestController
@RequestMapping("/api/map")

public class MapController {
    private final GetMapStopsUseCase getMapStopsUseCase;
    private final GetStopDeparturesUseCase getStopDeparturesUseCase;

    public MapController(GetMapStopsUseCase getMapStopsUseCase, GetStopDeparturesUseCase getStopDeparturesUseCase) {
        this.getMapStopsUseCase = getMapStopsUseCase;
        this.getStopDeparturesUseCase = getStopDeparturesUseCase;
    }

   @GetMapping("/stops")
   public List<MapStopDto> getStops(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String modes,
            @RequestParam(defaultValue = "true") boolean live
    ) {
        Set<TransportMode> modeSet = parseModes(modes);

        List<TransportStop> stops = getMapStopsUseCase.execute(location, modeSet, live);

        return stops.stream()
                .map(s -> new MapStopDto(s.getID(), s.getName(), s.getLatitude(), s.getLongitude(), s.getMode()))
                .collect(Collectors.toList());
    }

    @GetMapping("/stops/{id}/departures")
    public List<DepartureDto> getDepartures(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean live
    ) {
        List<Departure> departures = getStopDeparturesUseCase.execute(id, live);

        return departures.stream()
                .map(d -> new DepartureDto(d.getRouteName(), d.getMinutesUntilArrival()))
                .collect(Collectors.toList());
    }

    /**
     * Converts "BUS,TRAIN" into a Set<TransportMode>.
     * If null/blank, returns empty set meaning "no filter".
     */
    private Set<TransportMode> parseModes(String modes) {
        if (modes == null || modes.isBlank()) {
            return Collections.emptySet();
        }

        Set<TransportMode> result = new HashSet<>();
        for (String token : modes.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(TransportMode.valueOf(trimmed));
            }
        }
        return result;
    }
}
