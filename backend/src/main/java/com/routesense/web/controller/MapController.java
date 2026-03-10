



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

// Controller for map-related endpoints.
// Handles requests from the frontend for displaying stops on the map and showing live departure info when a stop is clicked.
@RestController
@RequestMapping("/api/map")

// The MapController is responsible for handling API requests related to the map view, including:
// - Retrieving the list of stops to display on the map, filtered by location and transport modes
// - Retrieving live departure information for a specific stop when the user clicks on it
public class MapController {
    private final GetMapStopsUseCase getMapStopsUseCase;
    private final GetStopDeparturesUseCase getStopDeparturesUseCase;

    public MapController(GetMapStopsUseCase getMapStopsUseCase, GetStopDeparturesUseCase getStopDeparturesUseCase) {
        this.getMapStopsUseCase = getMapStopsUseCase;
        this.getStopDeparturesUseCase = getStopDeparturesUseCase;
    }

    // GET /api/map/stops?location=Galway&modes=BUS,TRAIN&live=true
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

    // GET /api/map/stops/{id}/departures?live=true
    @GetMapping("/stops/{id}/departures")
    public List<DepartureDto> getDepartures(
            @PathVariable String id,
            @RequestParam(defaultValue = "true") boolean live
    ) {
        List<Departure> departures = getStopDeparturesUseCase.execute(id, live);

        return departures.stream()
                .map(d -> new DepartureDto(d.getRouteName(), d.getMinutesUntilArrival()))
                .collect(Collectors.toList());
    }

    // Helper method to parse the comma-separated transport modes from the query parameter into a Set<TransportMode>.
    // If the input is null or blank, returns an empty set which means "no filter, show all modes".
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
