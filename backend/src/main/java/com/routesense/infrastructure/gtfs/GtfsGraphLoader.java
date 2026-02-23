package com.routesense.infrastructure.gtfs;

import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;
import org.slf4j.Logger; //loggging events at run time, for debugging and monitoring purposes
import org.slf4j.LoggerFactory; //used to create Logger instances for specific classes, allowing for organized and contextual logging output
import org.springframework.core.io.ClassPathResource; //used to access internal files from the application's classpath.
import org.springframework.stereotype.Component; //marks this class as a Spring-managed component

import jakarta.annotation.PostConstruct; //set-up method that runs after the object is ready
import java.io.BufferedReader; //used for efficient reading of text files line by line
import java.io.IOException; //
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads a stop graph from GTFS static files into memory at startup.
 */
@Component
public class GtfsGraphLoader {

    private static final int DEFAULT_EDGE_TIME_SECONDS = 120;
    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsGraphLoader.class);

    private Map<String, Stop> stops = Map.of();
    private Map<String, List<StopEdge>> adjacencyList = Map.of();

    @PostConstruct
    public void loadGraph() {
        Map<String, Stop> parsedStops = loadStops();
        Map<String, List<StopEdge>> parsedAdjacency = loadEdges(parsedStops);

        this.stops = Collections.unmodifiableMap(parsedStops);

        Map<String, List<StopEdge>> immutableAdjacency = new HashMap<>();
        for (Map.Entry<String, List<StopEdge>> entry : parsedAdjacency.entrySet()) {
            immutableAdjacency.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.adjacencyList = Collections.unmodifiableMap(immutableAdjacency);
    }

    public Map<String, Stop> getStops() {
        return stops;
    }

    public Map<String, List<StopEdge>> getAdjacencyList() {
        return adjacencyList;
    }

    private Map<String, Stop> loadStops() {
        Map<String, Stop> result = new HashMap<>();

        try (BufferedReader reader = openGtfsReader("stops.txt")) {
            String header = reader.readLine();
            if (header == null) {
                return result;
            }

            Map<String, Integer> columnIndex = buildColumnIndex(header);
            Integer stopIdIdx = columnIndex.get("stop_id");
            Integer stopNameIdx = columnIndex.get("stop_name");
            Integer stopLatIdx = columnIndex.get("stop_lat");
            Integer stopLonIdx = columnIndex.get("stop_lon");

            if (stopIdIdx == null || stopNameIdx == null || stopLatIdx == null || stopLonIdx == null) {
                return result;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                List<String> tokens = parseCsvLine(line);
                String stopId = getValue(tokens, stopIdIdx);
                String stopName = getValue(tokens, stopNameIdx);
                String stopLat = getValue(tokens, stopLatIdx);
                String stopLon = getValue(tokens, stopLonIdx);

                if (isBlank(stopId) || isBlank(stopLat) || isBlank(stopLon)) {
                    continue;
                }

                try {
                    double latitude = Double.parseDouble(stopLat);
                    double longitude = Double.parseDouble(stopLon);
                    result.put(stopId, new Stop(stopId, stopName, latitude, longitude));
                } catch (NumberFormatException ignored) {
                    // Skip malformed rows.
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load GTFS stops.txt", e);
        }

        return result;
    }

    private Map<String, List<StopEdge>> loadEdges(Map<String, Stop> loadedStops) {
        Map<String, StopEdgeStats> aggregatedByLink = new HashMap<>();

        try (BufferedReader reader = openGtfsReader("stop_times.txt")) {
            String header = reader.readLine();
            if (header == null) {
                return Map.of();
            }

            Map<String, Integer> columnIndex = buildColumnIndex(header);
            Integer tripIdIdx = columnIndex.get("trip_id");
            Integer arrivalTimeIdx = columnIndex.get("arrival_time");
            Integer stopIdIdx = columnIndex.get("stop_id");
            Integer stopSequenceIdx = columnIndex.get("stop_sequence");

            if (tripIdIdx == null || arrivalTimeIdx == null || stopIdIdx == null || stopSequenceIdx == null) {
                return Map.of();
            }

            String currentTripId = null;
            List<StopTimeRow> currentTripRows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> tokens = parseCsvLine(line);

                String tripId = getValue(tokens, tripIdIdx);
                String stopId = getValue(tokens, stopIdIdx);
                String arrivalTime = getValue(tokens, arrivalTimeIdx);
                String stopSequence = getValue(tokens, stopSequenceIdx);

                if (isBlank(tripId) || isBlank(stopId) || isBlank(arrivalTime) || isBlank(stopSequence)) {
                    continue;
                }

                if (!loadedStops.containsKey(stopId)) {
                    continue;
                }

                int sequence;
                int arrivalSeconds;
                try {
                    sequence = Integer.parseInt(stopSequence);
                    arrivalSeconds = parseGtfsTimeToSeconds(arrivalTime);
                } catch (NumberFormatException ex) {
                    continue;
                }

                if (currentTripId == null) {
                    currentTripId = tripId;
                }

                if (!currentTripId.equals(tripId)) {
                    processTripRows(currentTripRows, aggregatedByLink);
                    currentTripRows.clear();
                    currentTripId = tripId;
                }

                currentTripRows.add(new StopTimeRow(stopId, sequence, arrivalSeconds));
            }

            processTripRows(currentTripRows, aggregatedByLink);
        } catch (IOException e) {
            LOGGER.warn("GTFS stop_times.txt not found or unreadable; stop graph edges will be empty", e);
            return Map.of();
        }

        Map<String, List<StopEdge>> adjacency = new HashMap<>();
        for (StopEdgeStats stats : aggregatedByLink.values()) {
            StopEdge edge = new StopEdge(stats.fromStopId(), stats.toStopId(), stats.averageTravelSeconds());
            adjacency.computeIfAbsent(edge.getFromStopId(), ignored -> new ArrayList<>()).add(edge);
        }

        return adjacency;
    }

    private void processTripRows(List<StopTimeRow> tripRows, Map<String, StopEdgeStats> aggregatedByLink) {
        if (tripRows.size() < 2) {
            return;
        }

        tripRows.sort(Comparator.comparingInt(StopTimeRow::sequence));

        for (int i = 1; i < tripRows.size(); i++) {
            StopTimeRow from = tripRows.get(i - 1);
            StopTimeRow to = tripRows.get(i);

            if (from.stopId().equals(to.stopId())) {
                continue;
            }

            int travelSeconds = Math.max(DEFAULT_EDGE_TIME_SECONDS, to.arrivalSeconds() - from.arrivalSeconds());
            String key = from.stopId() + "->" + to.stopId();

            StopEdgeStats stats = aggregatedByLink.computeIfAbsent(key, ignored -> new StopEdgeStats(from.stopId(), to.stopId()));
            stats.addObservation(travelSeconds);
        }
    }

    private BufferedReader openGtfsReader(String fileName) throws IOException {
        String[] candidatePaths = new String[] {
                "gtfs/" + fileName,
                "gtfs/google_transit.zip/" + fileName,
                "GTFS_Realtime.zip/" + fileName
        };

        for (String candidatePath : candidatePaths) {
            ClassPathResource candidate = new ClassPathResource(candidatePath);
            if (candidate.exists()) {
                InputStream inputStream = candidate.getInputStream();
                return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
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
        if (index < 0 || index >= tokens.size()) {
            return "";
        }
        return tokens.get(index).trim();
    }

    private int parseGtfsTimeToSeconds(String hhmmss) {
        String[] parts = hhmmss.split(":");
        if (parts.length != 3) {
            throw new NumberFormatException("Invalid GTFS time: " + hhmmss);
        }

        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);
        return (hours * 3600) + (minutes * 60) + seconds;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record StopTimeRow(String stopId, int sequence, int arrivalSeconds) {
    }

    private static class StopEdgeStats {
        private final String fromStopId;
        private final String toStopId;
        private int totalTravelSeconds;
        private int sampleCount;

        StopEdgeStats(String fromStopId, String toStopId) {
            this.fromStopId = fromStopId;
            this.toStopId = toStopId;
        }

        void addObservation(int travelSeconds) {
            this.totalTravelSeconds += travelSeconds;
            this.sampleCount++;
        }

        String fromStopId() {
            return fromStopId;
        }

        String toStopId() {
            return toStopId;
        }

        int averageTravelSeconds() {
            if (sampleCount == 0) {
                return DEFAULT_EDGE_TIME_SECONDS;
            }
            return Math.max(DEFAULT_EDGE_TIME_SECONDS, totalTravelSeconds / sampleCount);
        }
    }
}
