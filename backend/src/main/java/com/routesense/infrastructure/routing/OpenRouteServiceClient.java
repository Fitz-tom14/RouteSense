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

    // Calls the ORS API to get a driving route between the origin and destination coordinates.
    // Returns an Optional<CarRoute> which contains the distance, duration, and geometry of the route if successful, or empty if there was an error or if the API key is not configured.
    public Optional<CarRoute> getDrivingRoute(double originLat, double originLon,double destLat, double destLon) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            // ORS expects coordinates as "longitude,latitude" (note: lon before lat)
            String url = String.format(Locale.US, "%s?api_key=%s&start=%f,%f&end=%f,%f",
                    ORS_URL, apiKey, originLon, originLat, destLon, destLat);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.warn("OpenRouteService returned HTTP {}", response.statusCode());
                return Optional.empty();
            }

            return parseResponse(response.body());

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

    /**
     * The result of a successful driving route lookup.
     * geometry is a list of [lat, lon] pairs ready for Leaflet, or null if unavailable.
     */
    public record CarRoute(int durationSeconds, double distanceKm, List<List<Double>> geometry) {}
}
