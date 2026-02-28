package com.routesense.infrastructure.gtfs;

import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


//Loads all stopd from stops.txt and builds a graph of stop->stop edges with travel times based on stop_times.txt.
// build connections between stops from stop_times.txt
@Component
public class GtfsGraphLoader {

    // Used if we cant calculate a real travel time between two stops
    private static final int DEFAULT_EDGE_TIME_SECONDS = 120;

    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsGraphLoader.class);

    // Loaded once at startup and then treated as read-only
    private Map<String, Stop> stops = Map.of();
    private Map<String, List<StopEdge>> adjacencyList = Map.of();

    // Build the in-memory graph when Spring starts the app
    @PostConstruct
    public void loadGraph() {
        Map<String, Stop> parsedStops = loadStops();
        Map<String, List<StopEdge>> parsedAdjacency = loadEdges(parsedStops);

        // Freeze the maps so nothing accidentally edits them later
        this.stops = Collections.unmodifiableMap(parsedStops);

        Map<String, List<StopEdge>> immutableAdjacency = new HashMap<>();
        for (Map.Entry<String, List<StopEdge>> entry : parsedAdjacency.entrySet()) {
            immutableAdjacency.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.adjacencyList = Collections.unmodifiableMap(immutableAdjacency);
    }

    // Expose the loaded graph data to the rest of the app via these getters
    public Map<String, Stop> getStops() {
        return stops;
    }

    // Returns a map where the key is a stop ID and the value is a list of edges 
    public Map<String, List<StopEdge>> getAdjacencyList() {
        return adjacencyList;
    }

    // Loads stops from GTFS stops.txt and returns a map of stop_id to Stop objects
    private Map<String, Stop> loadStops() {
        Map<String, Stop> result = new HashMap<>();

        // We look for stops.txt in a few different places to be flexible with how the GTFS data is provided
        try (BufferedReader reader = openGtfsReader("stops.txt")) {
            String header = reader.readLine();
            if (header == null) {
                return result;
            }

            // Build a map of column name to index for easy lookup
            Map<String, Integer> columnIndex = buildColumnIndex(header);
            Integer stopIdIdx = columnIndex.get("stop_id");
            Integer stopNameIdx = columnIndex.get("stop_name");
            Integer stopLatIdx = columnIndex.get("stop_lat");
            Integer stopLonIdx = columnIndex.get("stop_lon");

            // If any of the required columns are missing, we can’t load stops
            if (stopIdIdx == null || stopNameIdx == null || stopLatIdx == null || stopLonIdx == null) {
                return result;
            }

            // Read each line of the stops.txt file, parse the CSV, and create Stop objects
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

                // Parse latitude and longitude, and skip rows with invalid coordinates
                try {
                    double latitude = Double.parseDouble(stopLat);
                    double longitude = Double.parseDouble(stopLon);
                    result.put(stopId, new Stop(stopId, stopName, latitude, longitude));
                } catch (NumberFormatException ignored) {
                    // just skip bad rows
                }
            }
            // We should have a map of all stops by their ID now
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load GTFS stops.txt", e);
        }

        return result;
    }

    
    private Map<String, List<StopEdge>> loadEdges(Map<String, Stop> loadedStops) {
        // We build edges by walking stop_times within each trip and measuring time gaps
        Map<String, StopEdgeStats> aggregatedByLink = new HashMap<>();

        // Similar to stops.txt, we look for stop_times.txt in a few different places to be flexible
        try (BufferedReader reader = openGtfsReader("stop_times.txt")) {
            String header = reader.readLine();
            if (header == null) {
                return Map.of();
            }

            // Build a map of column name to index for easy lookup
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

                // Required fields for building edges; skip rows that are missing any of these
                String tripId = getValue(tokens, tripIdIdx);
                String stopId = getValue(tokens, stopIdIdx);
                String arrivalTime = getValue(tokens, arrivalTimeIdx);
                String stopSequence = getValue(tokens, stopSequenceIdx);

                // If any of the critical fields are blank, we can’t use this row to build edges
                if (isBlank(tripId) || isBlank(stopId) || isBlank(arrivalTime) || isBlank(stopSequence)) {
                    continue;
                }

                // Ignore stop_times rows that reference stops we didn’t load
                if (!loadedStops.containsKey(stopId)) {
                    continue;
                }

                // Parse the stop sequence and arrival time, and skip rows with invalid formats
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

                // New trip -> finish the last one
                if (!currentTripId.equals(tripId)) {
                    processTripRows(currentTripRows, aggregatedByLink);
                    currentTripRows.clear();
                    currentTripId = tripId;
                }

                currentTripRows.add(new StopTimeRow(stopId, sequence, arrivalSeconds));
            }

            // last trip in file
            processTripRows(currentTripRows, aggregatedByLink);
        } catch (IOException e) {
            LOGGER.warn("GTFS stop_times.txt not found or unreadable; stop graph edges will be empty", e);
            return Map.of();
        }

        // Now we have aggregated stats for each stop->stop link, we can build the final adjacency list with average travel times
        Map<String, List<StopEdge>> adjacency = new HashMap<>();
        for (StopEdgeStats stats : aggregatedByLink.values()) {
            StopEdge edge = new StopEdge(stats.fromStopId(), stats.toStopId(), stats.averageTravelSeconds());
            adjacency.computeIfAbsent(edge.getFromStopId(), ignored -> new ArrayList<>()).add(edge);
        }

        return adjacency;
    }

    // Takes all the stop times for a single trip, calculates travel times between consecutive stops, and aggregates those times by stop->stop link
    private void processTripRows(List<StopTimeRow> tripRows, Map<String, StopEdgeStats> aggregatedByLink) {
        if (tripRows.size() < 2) {
            return;
        }

        tripRows.sort(Comparator.comparingInt(StopTimeRow::sequence));

        // Walk through the stop times in order and calculate travel time between each pair of consecutive stops, then aggregate those times by the from->to stop pair
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

    // Tries to open a GTFS file from several possible locations within the classpath, returning a BufferedReader if found or throwing an exception if not found
    private BufferedReader openGtfsReader(String fileName) throws IOException {
        String[] candidatePaths = new String[] {
                "gtfs/" + fileName,
                "gtfs/google_transit.zip/" + fileName,
                "GTFS_Realtime.zip/" + fileName
        };

        for (String candidatePath : candidatePaths) {
            if (candidatePath == null) {
                continue;
            }
            ClassPathResource candidate = new ClassPathResource(candidatePath);
            if (candidate.exists()) {
                InputStream inputStream = candidate.getInputStream();
                return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            }
        }

        throw new IOException("GTFS file not found: " + fileName);
    }

    // Parses the header line of a GTFS CSV file and builds a map of column name to its index, so we can easily look up columns by name later
    private Map<String, Integer> buildColumnIndex(String headerLine) {
        List<String> columns = parseCsvLine(headerLine);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            index.put(columns.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return index;
    }

    // Tiny CSV parser that handles quoted commas (
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

    // Helper to safely get a value from a list of tokens by index, returning an empty string if the index is out of bounds
    private String getValue(List<String> tokens, int index) {
        if (index < 0 || index >= tokens.size()) {
            return "";
        }
        return tokens.get(index).trim();
    }

    // GTFS times are in HH:MM:SS format, but can exceed 24 hours for trips that go past midnight, so we need to parse them into total seconds
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

    // Simple record to hold stop times data for a single stop within a trip, used for processing stop_times.txt
    private record StopTimeRow(String stopId, int sequence, int arrivalSeconds) {}

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