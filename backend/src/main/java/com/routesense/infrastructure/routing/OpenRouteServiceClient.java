package com.routesense.infrastructure.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Client for calling the OpenRouteService API to get real driving distances and durations between two points.
// This is used to calculate the car baseline for each journey search, so we can compare the emissions of the transit option against a realistic car route instead of just the straight-line distance.
@Component
public class OpenRouteServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouteServiceClient.class);
    private static final String ORS_URL = "https://api.openrouteservice.org/v2/directions/driving-car";

    @Value("${openrouteservice.api.key:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Calls the ORS API to get the fastest driving route between the origin and destination coordinates.
    // Uses the POST endpoint with "preference": "fastest" so the result reflects the quickest road route
    // rather than the default "recommended" balance of distance and time.
    // Returns an Optional<CarRoute> which contains the distance, duration, and geometry of the route if successful, or empty if there was an error or if the API key is not configured.
    public Optional<CarRoute> getDrivingRoute(double originLat, double originLon, double destLat, double destLon) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        // Build the JSON request body with the origin and destination coordinates, and set the preference to "fastest" to get the quickest route.
        try {
            String body = String.format(Locale.US,
                    "{\"coordinates\":[[%f,%f],[%f,%f]],\"preference\":\"fastest\",\"geometry\":true}",
                    originLon, originLat, destLon, destLat);

                    // Build the HTTP POST request with the appropriate headers and body.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ORS_URL))
                    .header("Accept", "application/json, application/geo+json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                    // Send the request and get the response.  If the status code is not 200, log a warning and return empty.
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.warn("OpenRouteService returned HTTP {}", response.statusCode());
                return Optional.empty();
            }

            return parseResponse(response.body());

            // If any exceptions occur during the request or response parsing, log a warning and return empty.
        } catch (Exception e) {
            LOGGER.warn("OpenRouteService call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // Parses the ORS GeoJSON response to extract distance, duration, and route geometry.
    // Response path: features[0].properties.segments[0].distance / .duration
    //                features[0].geometry.coordinates   -> [[lon,lat], ...]
    private Optional<CarRoute> parseResponse(String json) {
        try {
            JsonNode root     = objectMapper.readTree(json);
            JsonNode feature  = root.path("features").get(0);
            JsonNode segment  = feature.path("properties").path("segments").get(0);

            // Extract distance in meters and duration in seconds from the response.
            double distanceMetres = segment.path("distance").asDouble();
            double durationSecs   = segment.path("duration").asDouble();

            // Extract route geometry: ORS gives [lon, lat] — convert to [[lat, lon]] for Leaflet
            List<List<Double>> geometry = new ArrayList<>();
            JsonNode coords = feature.path("geometry").path("coordinates");
            if (coords.isArray()) {
                for (JsonNode pt : coords) {
                    double lon = pt.get(0).asDouble();
                    double lat = pt.get(1).asDouble();
                    geometry.add(List.of(lat, lon));
                }
            }

            // Create and return a CarRoute object with the extracted distance, duration, and geometry.
            return Optional.of(new CarRoute(
                    (int) Math.round(durationSecs),
                    distanceMetres / 1000.0,
                    geometry.isEmpty() ? null : geometry
            ));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse OpenRouteService response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    
     //The result of a successful driving route lookup.
     //geometry is a list of [lat, lon] pairs ready for Leaflet, or null if unavailable.
     
    public record CarRoute(int durationSeconds, double distanceKm, List<List<Double>> geometry) {}
}
