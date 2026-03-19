package com.routesense.infrastructure.map;

import com.routesense.application.port.MapDataSource;
import com.routesense.domain.model.*;
import com.routesense.infrastructure.gtfs.GtfsGraphLoader;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

// In-memory implementation of MapDataSource that serves stop and departure data for the map from the GTFS-loaded graph.
// This is used by MapController to get the list of stops to display on the map, and to get live departure info for each stop when the user clicks on it.
@Component
public class InMemoryMapDataSource implements MapDataSource {

    // [minLat, minLng, maxLat, maxLng] bounding boxes for each city
    private static final Map<String, double[]> CITY_BOUNDS = Map.of(
        "Galway", new double[]{ 53.20, -9.20, 53.35, -8.85 },
        "Dublin", new double[]{ 53.28, -6.45, 53.42, -6.10 },
        "Cork",   new double[]{ 51.84, -8.60, 51.93, -8.38 }
    );

    private final GtfsGraphLoader loader;

    // Constructor injection of the GTFS graph loader, which provides access to the stops, edges, and schedules loaded from GTFS.
    public InMemoryMapDataSource(GtfsGraphLoader loader) {
        this.loader = loader;
    }

    // Retrieves all transport stops in a given location, filtered by transport modes and live status.
    // The location is used to filter stops within the city's bounding box. The modes set is used to filter by transport mode (e.g. BUS, TRAIN), and the live flag can be used to toggle between showing all stops vs only those with live departures.
    @Override
    public List<TransportStop> getAllStops(String location, Set<TransportMode> modes, boolean live) {
        double[] bounds = CITY_BOUNDS.getOrDefault(location, CITY_BOUNDS.get("Galway"));
        double minLat = bounds[0], minLng = bounds[1], maxLat = bounds[2], maxLng = bounds[3];

        Map<String, List<StopEdge>>            adjacency = loader.getAdjacencyList();
        Map<String, List<ScheduledConnection>> schedule  = loader.getSchedule();

        List<TransportStop> result = new ArrayList<>();

        for (Stop stop : loader.getStops().values()) {
            // Must be inside the city bounding box
            if (stop.getLatitude()  < minLat || stop.getLatitude()  > maxLat) continue;
            if (stop.getLongitude() < minLng || stop.getLongitude() > maxLng) continue;

            // Only show stops that have scheduled departures (skip ghost stops)
            if (!schedule.containsKey(stop.getId())) continue;

            // Determine the mode from outbound edges; default to BUS if unknown
            TransportMode mode = getModeForStop(stop.getId(), adjacency);

            // Apply the mode filter from the UI (empty set = show all)
            if (!modes.isEmpty() && !modes.contains(mode)) continue;

            result.add(new TransportStop(stop.getId(), stop.getName(),
                    stop.getLatitude(), stop.getLongitude(), mode));
        }

        return result;
    }

    // Looks at the first outbound edge to determine the stop's transport mode.
    private TransportMode getModeForStop(String stopId, Map<String, List<StopEdge>> adjacency) {
        List<StopEdge> edges = adjacency.get(stopId);
        if (edges == null || edges.isEmpty()) return TransportMode.BUS;
        TransportMode mode = edges.get(0).getTransportMode();
        return mode != null ? mode : TransportMode.BUS;
    }

    // Retrieves the next departures for a given stop ID. If live=true, only returns departures after the current time;
    // otherwise returns all scheduled departures.
    @Override
    public List<Departure> getDeparturesForStop(String stopId, boolean live) {
        List<ScheduledConnection> connections = loader.getSchedule().get(stopId);
        if (connections == null || connections.isEmpty()) return List.of();

        // Find departures after the current time and return the next 5
        int nowSeconds = LocalTime.now().toSecondOfDay();

        return connections.stream()
                .filter(c -> c.getDepartureTimeSeconds() > nowSeconds)
                .sorted(Comparator.comparingInt(ScheduledConnection::getDepartureTimeSeconds))
                .limit(5)
                .map(c -> {
                    int minsUntil = (c.getDepartureTimeSeconds() - nowSeconds) / 60;
                    String label = (c.getRouteShortName() != null && !c.getRouteShortName().isBlank())
                            ? c.getRouteShortName()
                            : c.getRouteId();
                    int totalMins = c.getDepartureTimeSeconds() / 60;
                    String scheduledTime = String.format("%02d:%02d", (totalMins / 60) % 24, totalMins % 60);
                    return new Departure(label, minsUntil, scheduledTime);
                })
                .collect(Collectors.toList());
    }
}
