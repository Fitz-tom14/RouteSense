package com.routesense.infrastructure.gtfs;

import com.routesense.domain.model.ScheduledConnection;
import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;
import com.routesense.domain.model.TransportMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Loads GTFS data from the classpath and builds in-memory graph structures for stops, edges, and schedules.
/// This is used by InMemoryStopGraphRepository to serve data to the application, and by InMemoryMapDataSource to serve stop and departure data to the map.
@Component
public class GtfsGraphLoader {

    private static final int DEFAULT_EDGE_TIME_SECONDS = 120;
    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsGraphLoader.class);

    private Map<String, Stop> stops = Map.of();
    private Map<String, List<StopEdge>> adjacencyList = Map.of();
    private Map<String, List<ScheduledConnection>> scheduleByStop = Map.of();
    private Map<String, String> routeShortNames = Map.of(); // routeId → shortName (e.g. "405", "DART")

    @PostConstruct
    public void loadGraph() {
        Map<String, Stop> parsedStops = loadStops();
        LoadedEdgeData edgeData = loadEdges(parsedStops);

        this.stops = Collections.unmodifiableMap(parsedStops);

        // Freeze adjacency list
        Map<String, List<StopEdge>> immutableAdjacency = new HashMap<>();
        for (Map.Entry<String, List<StopEdge>> entry : edgeData.adjacencyList().entrySet()) {
            immutableAdjacency.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.adjacencyList = Collections.unmodifiableMap(immutableAdjacency);

        // Sort each stop's schedule by departure time, then freeze
        Map<String, List<ScheduledConnection>> immutableSchedule = new HashMap<>();
        for (Map.Entry<String, List<ScheduledConnection>> entry : edgeData.scheduleByStop().entrySet()) {
            List<ScheduledConnection> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparingInt(ScheduledConnection::getDepartureTimeSeconds));
            immutableSchedule.put(entry.getKey(), List.copyOf(sorted));
        }
        this.scheduleByStop = Collections.unmodifiableMap(immutableSchedule);

        // Build routeId → shortName lookup from routes.txt (used to label legs in the fallback Dijkstra)
        Map<String, String> shortNameMap = new HashMap<>();
        for (Map.Entry<String, RouteInfo> entry : loadRouteInfoByRouteId().entrySet()) {
            if (entry.getValue().shortName() != null && !entry.getValue().shortName().isBlank()) {
                shortNameMap.put(entry.getKey(), entry.getValue().shortName());
            }
        }
        this.routeShortNames = Collections.unmodifiableMap(shortNameMap);

        LOGGER.info("GTFS loaded: {} stops, {} stops with edges, {} stops with schedule data, {} routes with short names",
                parsedStops.size(), immutableAdjacency.size(), immutableSchedule.size(), shortNameMap.size());
    }

    public Map<String, Stop> getStops() {
        return stops;
    }

    public Map<String, List<StopEdge>> getAdjacencyList() {
        return adjacencyList;
    }

    public Map<String, List<ScheduledConnection>> getSchedule() {
        return scheduleByStop;
    }

    public Map<String, String> getRouteShortNames() {
        return routeShortNames;
    }

    // -------------------------------------------------------------------------
    // Stop loading
    // -------------------------------------------------------------------------

    private Map<String, Stop> loadStops() {
        Map<String, Stop> result = new HashMap<>();

        try (BufferedReader reader = openGtfsReader("stops.txt")) {
            String header = reader.readLine();
            if (header == null) return result;

            Map<String, Integer> col = buildColumnIndex(header);
            Integer stopIdIdx    = col.get("stop_id");
            Integer stopNameIdx  = col.get("stop_name");
            Integer stopLatIdx   = col.get("stop_lat");
            Integer stopLonIdx   = col.get("stop_lon");

            if (stopIdIdx == null || stopNameIdx == null || stopLatIdx == null || stopLonIdx == null) {
                return result;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                List<String> tokens = parseCsvLine(line);
                String stopId  = getValue(tokens, stopIdIdx);
                String name    = getValue(tokens, stopNameIdx);
                String lat     = getValue(tokens, stopLatIdx);
                String lon     = getValue(tokens, stopLonIdx);

                if (isBlank(stopId) || isBlank(lat) || isBlank(lon)) continue;

                try {
                    result.put(stopId, new Stop(stopId, name, Double.parseDouble(lat), Double.parseDouble(lon)));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load GTFS stops.txt", e);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Edge + schedule loading
    // -------------------------------------------------------------------------

    // Groups both output maps into one return value so loadEdges() can return both at once.
    private record LoadedEdgeData(
            Map<String, List<StopEdge>> adjacencyList,
            Map<String, List<ScheduledConnection>> scheduleByStop) {}

    private LoadedEdgeData loadEdges(Map<String, Stop> loadedStops) {
        // aggregatedByLink: keyed by "fromStopId->toStopId:routeId" to keep each route's edge separate.
        // This is what fixes the 402/405 blending problem - each route now has its own average edge.
        Map<String, StopEdgeStats> aggregatedByLink = new HashMap<>();
        Map<String, List<ScheduledConnection>> scheduleByStop  = new HashMap<>();

        Map<String, TripInfo> tripInfoByTripId = loadTripInfoByTripId();

        try (BufferedReader reader = openGtfsReader("stop_times.txt")) {
            String header = reader.readLine();
            if (header == null) return new LoadedEdgeData(Map.of(), Map.of());

            Map<String, Integer> col = buildColumnIndex(header);
            Integer tripIdIdx       = col.get("trip_id");
            Integer arrivalTimeIdx  = col.get("arrival_time");
            Integer stopIdIdx       = col.get("stop_id");
            Integer stopSeqIdx      = col.get("stop_sequence");

            if (tripIdIdx == null || arrivalTimeIdx == null || stopIdIdx == null || stopSeqIdx == null) {
                return new LoadedEdgeData(Map.of(), Map.of());
            }

            String currentTripId = null;
            TripInfo currentTripInfo = null;
            List<StopTimeRow> currentTripRows = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                List<String> tokens = parseCsvLine(line);

                String tripId       = getValue(tokens, tripIdIdx);
                String stopId       = getValue(tokens, stopIdIdx);
                String arrivalTime  = getValue(tokens, arrivalTimeIdx);
                String stopSequence = getValue(tokens, stopSeqIdx);

                if (isBlank(tripId) || isBlank(stopId) || isBlank(arrivalTime) || isBlank(stopSequence)) continue;
                if (!loadedStops.containsKey(stopId)) continue;

                int sequence;
                int arrivalSeconds;
                try {
                    sequence       = Integer.parseInt(stopSequence);
                    arrivalSeconds = parseGtfsTimeToSeconds(arrivalTime);
                } catch (NumberFormatException ex) {
                    continue;
                }

                if (currentTripId == null) {
                    currentTripId   = tripId;
                    currentTripInfo = tripInfoByTripId.get(tripId);
                }

                if (!currentTripId.equals(tripId)) {
                    processTripRows(currentTripRows, currentTripInfo, aggregatedByLink, scheduleByStop);
                    currentTripRows.clear();
                    currentTripId   = tripId;
                    currentTripInfo = tripInfoByTripId.get(tripId);
                }

                currentTripRows.add(new StopTimeRow(stopId, sequence, arrivalSeconds));
            }

            // Process the last trip in the file
            processTripRows(currentTripRows, currentTripInfo, aggregatedByLink, scheduleByStop);

        } catch (IOException e) {
            LOGGER.warn("GTFS stop_times.txt not found or unreadable; graph will be empty", e);
            return new LoadedEdgeData(Map.of(), Map.of());
        }

        // Build the adjacency list from the aggregated edge stats
        Map<String, List<StopEdge>> adjacency = new HashMap<>();
        for (StopEdgeStats stats : aggregatedByLink.values()) {
            StopEdge edge = new StopEdge(
                    stats.fromStopId(),
                    stats.toStopId(),
                    stats.averageTravelSeconds(),
                    stats.dominantMode(),
                    stats.routeId()
            );
            adjacency.computeIfAbsent(edge.getFromStopId(), ignored -> new ArrayList<>()).add(edge);
        }

        return new LoadedEdgeData(adjacency, scheduleByStop);
    }

    /**
     * For a single trip, walks the stops in sequence order and:
     * 1. Aggregates average travel times per stop-pair per route (for the adjacency list).
     * 2. Creates a ScheduledConnection for each consecutive stop pair (for the schedule map).
     */
    private void processTripRows(
            List<StopTimeRow> tripRows,
            TripInfo tripInfo,
            Map<String, StopEdgeStats> aggregatedByLink,
            Map<String, List<ScheduledConnection>> scheduleByStop
    ) {
        if (tripRows.size() < 2) return;

        tripRows.sort(Comparator.comparingInt(StopTimeRow::sequence));

        String routeId    = tripInfo != null ? tripInfo.routeId()    : null;
        String shortName  = tripInfo != null ? tripInfo.shortName()  : null;
        TransportMode mode = tripInfo != null ? tripInfo.mode()      : null;

        for (int i = 1; i < tripRows.size(); i++) {
            StopTimeRow from = tripRows.get(i - 1);
            StopTimeRow to   = tripRows.get(i);

            if (from.stopId().equals(to.stopId())) continue;

            int travelSeconds = Math.max(DEFAULT_EDGE_TIME_SECONDS, to.arrivalSeconds() - from.arrivalSeconds());

            // Route-aware edge key: "from->to:routeId" keeps Bus 402 and Bus 405 separate
            String edgeKey = from.stopId() + "->" + to.stopId() + (routeId != null ? ":" + routeId : "");
            StopEdgeStats stats = aggregatedByLink.computeIfAbsent(
                    edgeKey, ignored -> new StopEdgeStats(from.stopId(), to.stopId(), routeId));
            stats.addObservation(travelSeconds, mode);

            // Only build schedule entries when we have a route ID - required for schedule-aware routing
            if (routeId != null) {
                ScheduledConnection conn = new ScheduledConnection(
                        routeId,
                        shortName,
                        to.stopId(),
                        from.arrivalSeconds(), // use arrival time as proxy for departure (GTFS often omits departure_time)
                        to.arrivalSeconds(),
                        mode
                );
                scheduleByStop.computeIfAbsent(from.stopId(), ignored -> new ArrayList<>()).add(conn);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Route and trip info loading
    // -------------------------------------------------------------------------

    // Holds the mode and short name for a GTFS route (e.g. mode=BUS, shortName="411")
    private record RouteInfo(TransportMode mode, String shortName) {}

    // Holds everything we need per trip: mode, the route's ID, and the route's short name
    private record TripInfo(TransportMode mode, String routeId, String shortName) {}

    private Map<String, TripInfo> loadTripInfoByTripId() {
        Map<String, RouteInfo> routeInfoByRouteId = loadRouteInfoByRouteId();
        if (routeInfoByRouteId.isEmpty()) return Map.of();

        Map<String, TripInfo> tripInfoByTripId = new HashMap<>();
        try (BufferedReader reader = openGtfsReader("trips.txt")) {
            String header = reader.readLine();
            if (header == null) return Map.of();

            Map<String, Integer> col = buildColumnIndex(header);
            Integer tripIdIdx   = col.get("trip_id");
            Integer routeIdIdx  = col.get("route_id");
            if (tripIdIdx == null || routeIdIdx == null) return Map.of();

            String line;
            while ((line = reader.readLine()) != null) {
                List<String> tokens = parseCsvLine(line);
                String tripId  = getValue(tokens, tripIdIdx);
                String routeId = getValue(tokens, routeIdIdx);
                if (isBlank(tripId) || isBlank(routeId)) continue;

                RouteInfo info = routeInfoByRouteId.get(routeId);
                if (info != null) {
                    tripInfoByTripId.put(tripId, new TripInfo(info.mode(), routeId, info.shortName()));
                }
            }
        } catch (IOException e) {
            LOGGER.warn("GTFS trips.txt not found or unreadable; route info may be missing", e);
        }

        return tripInfoByTripId;
    }

    private Map<String, RouteInfo> loadRouteInfoByRouteId() {
        Map<String, RouteInfo> result = new HashMap<>();
        try (BufferedReader reader = openGtfsReader("routes.txt")) {
            String header = reader.readLine();
            if (header == null) return Map.of();

            Map<String, Integer> col = buildColumnIndex(header);
            Integer routeIdIdx        = col.get("route_id");
            Integer routeTypeIdx      = col.get("route_type");
            Integer routeShortNameIdx = col.get("route_short_name"); // e.g. "411", "409", "DART"
            if (routeIdIdx == null || routeTypeIdx == null) return Map.of();

            String line;
            while ((line = reader.readLine()) != null) {
                List<String> tokens = parseCsvLine(line);
                String routeId   = getValue(tokens, routeIdIdx);
                String routeType = getValue(tokens, routeTypeIdx);
                if (isBlank(routeId) || isBlank(routeType)) continue;

                try {
                    TransportMode mode = mapGtfsRouteType(Integer.parseInt(routeType));
                    String shortName   = routeShortNameIdx != null ? getValue(tokens, routeShortNameIdx) : "";
                    result.put(routeId, new RouteInfo(mode, shortName));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            LOGGER.warn("GTFS routes.txt not found or unreadable; edge modes may be inferred", e);
        }

        return result;
    }

    private TransportMode mapGtfsRouteType(int routeType) {
        return switch (routeType) {
            case 0, 900, 901, 902, 903, 904, 905, 906 -> TransportMode.TRAM;
            case 1, 2, 100, 109 -> TransportMode.TRAIN;
            case 3, 700, 701, 702, 703, 704, 705, 706 -> TransportMode.BUS;
            default -> TransportMode.BUS;
        };
    }

    // -------------------------------------------------------------------------
    // CSV / file utilities (unchanged from original)
    // -------------------------------------------------------------------------

    private BufferedReader openGtfsReader(String fileName) throws IOException {
        String[] candidatePaths = {
                "gtfs/" + fileName,
                "gtfs/google_transit.zip/" + fileName,
                "GTFS_Realtime.zip/" + fileName
        };

        for (String path : candidatePaths) {
            ClassPathResource candidate = new ClassPathResource(path);
            if (candidate.exists()) {
                InputStream stream = candidate.getInputStream();
                return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        }

        throw new IOException("GTFS file not found: " + fileName);
    }

    private Map<String, Integer> buildColumnIndex(String headerLine) {
        List<String> columns = parseCsvLine(headerLine);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            index.put(columns.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return index;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (c == ',' && !insideQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString());
        return values;
    }

    private String getValue(List<String> tokens, int index) {
        if (index < 0 || index >= tokens.size()) return "";
        return tokens.get(index).trim();
    }

    private int parseGtfsTimeToSeconds(String hhmmss) {
        String[] parts = hhmmss.split(":");
        if (parts.length != 3) throw new NumberFormatException("Invalid GTFS time: " + hhmmss);
        return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // -------------------------------------------------------------------------
    // Inner helpers
    // -------------------------------------------------------------------------

    private record StopTimeRow(String stopId, int sequence, int arrivalSeconds) {}

    /**
     * Accumulates travel time observations for a stop-pair on a single route,
     * then produces an averaged StopEdge for the adjacency list.
     */
    private static class StopEdgeStats {
        private final String fromStopId;
        private final String toStopId;
        private final String routeId; // null only for trips whose route couldn't be resolved
        private int totalTravelSeconds;
        private int sampleCount;
        private final Map<TransportMode, Integer> modeCounts = new EnumMap<>(TransportMode.class);

        StopEdgeStats(String fromStopId, String toStopId, String routeId) {
            this.fromStopId = fromStopId;
            this.toStopId   = toStopId;
            this.routeId    = routeId;
        }

        void addObservation(int travelSeconds, TransportMode mode) {
            totalTravelSeconds += travelSeconds;
            sampleCount++;
            TransportMode resolved = mode == null ? TransportMode.BUS : mode;
            modeCounts.merge(resolved, 1, Integer::sum);
        }

        String fromStopId()       { return fromStopId; }
        String toStopId()         { return toStopId; }
        String routeId()          { return routeId; }

        int averageTravelSeconds() {
            return sampleCount == 0 ? DEFAULT_EDGE_TIME_SECONDS
                    : Math.max(DEFAULT_EDGE_TIME_SECONDS, totalTravelSeconds / sampleCount);
        }

        TransportMode dominantMode() {
            TransportMode best = TransportMode.BUS;
            int bestCount = -1;
            for (Map.Entry<TransportMode, Integer> entry : modeCounts.entrySet()) {
                if (entry.getValue() > bestCount) {
                    best      = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            return best;
        }
    }
}
