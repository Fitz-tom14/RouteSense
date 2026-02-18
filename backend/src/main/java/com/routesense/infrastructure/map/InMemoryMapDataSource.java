package com.routesense.infrastructure.map;

import com.routesense.application.port.MapDataSource;
import com.routesense.domain.model.Departure;
import com.routesense.domain.model.TransportMode;
import com.routesense.domain.model.TransportStop;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Temporary in-memory implementation for map data.
 *
 * This supports UI development and clean architecture:
 * - Use cases depend on MapDataSource (port)
 * - This class can be replaced later with a real API or DB-backed implementation
 */
@Component
public class InMemoryMapDataSource implements MapDataSource {

    // Ireland-based stops organized by location
    private final Map<String, List<TransportStop>> stopsByLocation = Map.of(
        "Galway", List.of(
            new TransportStop(1L, "Eyre Square", 53.2740, -9.0498, TransportMode.BUS),
            new TransportStop(2L, "Ceannt Station", 53.2689, -9.0520, TransportMode.TRAIN),
            new TransportStop(3L, "Galway Bus Station", 53.2744, -9.0490, TransportMode.BUS)
        ),
        "Dublin", List.of(
            new TransportStop(4L, "O'Connell Street", 53.3506, -6.2595, TransportMode.BUS),
            new TransportStop(5L, "Connolly Station", 53.3621, -6.2434, TransportMode.TRAIN),
            new TransportStop(6L, "Heuston Station", 53.6453, -6.2944, TransportMode.TRAIN),
            new TransportStop(7L, "Luas Abbey Street", 53.3525, -6.2606, TransportMode.TRAM),
            new TransportStop(8L, "Dublin Bus Depot", 53.3500, -6.2650, TransportMode.BUS)
        ),
        "Cork", List.of(
            new TransportStop(9L, "Patrick Street", 51.8985, -8.4756, TransportMode.BUS),
            new TransportStop(10L, "Kent Station", 51.8962, -8.4717, TransportMode.TRAIN),
            new TransportStop(11L, "Cork Bus Station", 51.8970, -8.4800, TransportMode.BUS)
        )
    );

    @Override
    public List<TransportStop> getAllStops(String location, Set<TransportMode> modes, boolean live) {
        // Default to Galway if location not specified
        String effectiveLocation = (location == null || location.isBlank()) ? "Galway" : location;
        
        // Get stops for the specified location
        List<TransportStop> base = stopsByLocation.getOrDefault(effectiveLocation, List.of());

        // If no modes selected, return all.
        if (modes == null || modes.isEmpty()) {
            return base;
        }

        return base.stream()
                .filter(s -> modes.contains(s.getMode()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Departure> getDeparturesForStop(long stopId, boolean live) {
        // Placeholder departures with Ireland routes. "live" toggles slightly different timings
        // to simulate real-time behaviour.
        int offset = live ? 0 : 2;

        // Galway routes
        if (Objects.equals(stopId, 1L)) {
            return List.of(
                    new Departure("401", 2 + offset),
                    new Departure("5", 5 + offset),
                    new Departure("9", 8 + offset)
            );
        }

        if (Objects.equals(stopId, 2L)) {
            return List.of(
                    new Departure("Galway-Dublin", 15 + offset),
                    new Departure("Galway-Cork", 45 + offset)
            );
        }

        if (Objects.equals(stopId, 3L)) {
            return List.of(
                    new Departure("414", 3 + offset),
                    new Departure("100", 12 + offset)
            );
        }

        // Dublin routes
        if (Objects.equals(stopId, 4L)) {
            return List.of(
                    new Departure("1", 2 + offset),
                    new Departure("15", 5 + offset),
                    new Departure("11", 8 + offset)
            );
        }

        if (Objects.equals(stopId, 5L)) {
            return List.of(
                    new Departure("Dublin-Galway", 10 + offset),
                    new Departure("Dublin-Cork", 35 + offset)
            );
        }

        if (Objects.equals(stopId, 6L)) {
            return List.of(
                    new Departure("Heuston-Cork", 20 + offset),
                    new Departure("Heuston-Galway", 50 + offset)
            );
        }

        if (Objects.equals(stopId, 7L)) {
            return List.of(
                    new Departure("Luas Red", 3 + offset),
                    new Departure("Luas Green", 7 + offset)
            );
        }

        if (Objects.equals(stopId, 8L)) {
            return List.of(
                    new Departure("16", 4 + offset),
                    new Departure("14", 10 + offset)
            );
        }

        // Cork routes
        if (Objects.equals(stopId, 9L)) {
            return List.of(
                    new Departure("200", 3 + offset),
                    new Departure("220", 9 + offset)
            );
        }

        if (Objects.equals(stopId, 10L)) {
            return List.of(
                    new Departure("Cork-Dublin", 25 + offset),
                    new Departure("Cork-Galway", 40 + offset)
            );
        }

        if (Objects.equals(stopId, 11L)) {
            return List.of(
                    new Departure("201", 5 + offset),
                    new Departure("210", 15 + offset)
            );
        }

        return List.of(
                new Departure("Local", 5 + offset),
                new Departure("Express", 20 + offset)
        );
    }
}
