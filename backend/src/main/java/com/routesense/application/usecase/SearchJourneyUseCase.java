package com.routesense.application.usecase;

import com.routesense.application.port.StopGraphRepository;
import com.routesense.application.service.EmissionsCalculator;
import com.routesense.domain.model.JourneyOption;
import com.routesense.domain.model.JourneyOptionType;
import com.routesense.domain.model.JourneySearchResult;
import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;
import com.routesense.domain.model.TransportMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Finds route options between two stops using a Dijkstra-based search.
 */
@Component
public class SearchJourneyUseCase {

    //Constants defined for scoring and routing logic.
    private static final int BALANCED_TRANSFER_PENALTY_SECONDS = 300;
    private static final double TIME_WEIGHT = 0.55;
    private static final double TRANSFERS_WEIGHT = 0.25;
    private static final double CO2_WEIGHT = 0.20;
    private static final double WALK_SPEED_KMH = 4.8;
    private static final int ROUTING_CANDIDATE_LIMIT = 8;//When looking for a neardby stop 8 stops in total
    private static final double ROUTING_CANDIDATE_RADIUS_KM = 20.0;//Radius in km to search for nearby stops

    //dependency on the stop graph repository and emissions calculator.
    private final StopGraphRepository stopGraphRepository;//Provides access to the stop graph data.
    private final EmissionsCalculator emissionsCalculator;//Used to calculate CO2 emissions for different transport modes and distances.

    // Constructor injection of dependencies.
    public SearchJourneyUseCase(
            StopGraphRepository stopGraphRepository,
            EmissionsCalculator emissionsCalculator
    ) {
        this.stopGraphRepository = stopGraphRepository;
        this.emissionsCalculator = emissionsCalculator;
    }

    // Main method to execute the journey search use case.
    public JourneySearchResult execute(String originStopId, Double originLat, Double originLon, String destinationStopId) {
        if (destinationStopId == null || destinationStopId.isBlank()) {
            return new JourneySearchResult(List.of(), 0.0);
        }

        //pulls the stop graph data from the repository, including the stops and their adjacency list.
        Map<String, Stop> stops = stopGraphRepository.getStops();
        Map<String, List<StopEdge>> adjacencyList = stopGraphRepository.getAdjacencyList();

        // Resolve effective origin: from a stop ID, or from lat/lon by finding the nearest connected stop.
        String effectiveOriginStopId = originStopId;
        int extraOriginWalkSeconds = 0;

        // If lat/lon is provided without a valid stop ID, find the nearest stop with outgoing edges to use as the effective origin.
        if (originLat != null && originLon != null && (originStopId == null || originStopId.isBlank())) {
            StopDistance nearest = findNearestConnectedStop(originLat, originLon, stops, adjacencyList);
            if (nearest == null) {
                return new JourneySearchResult(List.of(), 0.0);
            }
            effectiveOriginStopId = nearest.stopId();// The walking time from the provided coordinates to the effective origin stop is added as extra access time.
            extraOriginWalkSeconds = walkingSeconds(nearest.distanceKm());// This extra time is considered in the scoring of journey options to account for the initial walk to the stop.
        }

        //verify that the effective origin and destination stops exist in the graph. If not, return an empty result. (ID)
        if (effectiveOriginStopId == null || effectiveOriginStopId.isBlank()) {
            return new JourneySearchResult(List.of(), 0.0);
        }

        //verify that the effective origin and destination stops exist in the graph. If not, return an empty result.(Keys)
        if (!stops.containsKey(effectiveOriginStopId) || !stops.containsKey(destinationStopId)) {
            return new JourneySearchResult(List.of(), 0.0);
        }

        //List of journey options to be scored and recommended, and a set to track seen route signatures to avoid duplicates.
        List<JourneyOption> optionsToScore = new ArrayList<>(); //adding to a list of options to be scored and recommended later
        Set<String> seenSignatures = new HashSet<>();

        //Trys to find direct routes between the effective origin and destination stops. If a direct route exists, it can be added as a journey option without needing to consider nearby stops.
        RoutingAnchors anchors = resolveRoutingAnchors(effectiveOriginStopId, destinationStopId, stops, adjacencyList);
        if (anchors != null) {
            PathResult fastest = dijkstra( //Finds the fastest path based on travel time.
                anchors.routingOriginStopId(),
                anchors.routingDestinationStopId(),
                adjacencyList,
                edge -> edge.getTravelTimeSeconds()
            );
            PathResult fewestTransfers = dijkstra(//Finds the path with the fewest transfers by assigning a weight of 1 to each edge, effectively counting the number of stops (and thus transfers) in the path.
                anchors.routingOriginStopId(),
                anchors.routingDestinationStopId(),
                adjacencyList,
                edge -> 1
            );
            PathResult balanced = dijkstra(//Finds a balanced path by adding a fixed penalty to the travel time of each edge, which encourages routes with fewer transfers while still considering overall travel time.
                anchors.routingOriginStopId(),
                anchors.routingDestinationStopId(),
                adjacencyList,
                edge -> edge.getTravelTimeSeconds() + BALANCED_TRANSFER_PENALTY_SECONDS
            );

            //Results from these three searches are added as journey options, with the total duration including any extra walking time to access the stops.
            // The CO2 emissions for each option are calculated based on the modes of transport used and the distances traveled.
            int totalOriginAccessSeconds = anchors.originAccessSeconds() + extraOriginWalkSeconds;
            addPublicTransportOption(
                optionsToScore, seenSignatures, fastest, stops, adjacencyList,
                effectiveOriginStopId, destinationStopId, totalOriginAccessSeconds, anchors.destinationAccessSeconds()
            );
            addPublicTransportOption(
                optionsToScore, seenSignatures, fewestTransfers, stops, adjacencyList,
                effectiveOriginStopId, destinationStopId, totalOriginAccessSeconds, anchors.destinationAccessSeconds()
            );
            addPublicTransportOption(
                optionsToScore, seenSignatures, balanced, stops, adjacencyList,
                effectiveOriginStopId, destinationStopId, totalOriginAccessSeconds, anchors.destinationAccessSeconds()
            );
        }

        //Used to score and recommend the best journey options based on a weighted combination of total duration, number of transfers, and CO2 emissions.
        // The best option is identified and marked as recommended, with a reason provided for the recommendation.
        List<JourneyOption> scoredPublicOptions = new ArrayList<>(scoreAndRecommend(optionsToScore));
        scoredPublicOptions.sort(Comparator.comparingInt(JourneyOption::getTotalDurationSeconds)
                .thenComparingInt(JourneyOption::getTransfers)
                .thenComparingDouble(JourneyOption::getCo2Grams));

        //A car baseline option is generated for comparison, either from the provided geographic coordinates (if no valid origin stop ID was given) or from the effective origin stop.
        //This baseline estimates the travel time and CO2 emissions for driving directly from the origin to the destination.
        JourneyOption carBaseline;
        if (originLat != null && originLon != null && (originStopId == null || originStopId.isBlank())) {
            carBaseline = buildCarBaselineFromCoords(originLat, originLon, destinationStopId, stops);
        } else {
            carBaseline = buildCarBaseline(effectiveOriginStopId, destinationStopId, stops);
        }

        // The car baseline is added to the list of scored public transport options for comparison.
        // The final result includes all journey options along with the CO2 emissions of the car baseline for reference.
        List<JourneyOption> allOptions = new ArrayList<>(scoredPublicOptions);
        if (carBaseline != null) {
            allOptions.add(carBaseline);
        }

        double carBaselineCo2Grams = carBaseline == null ? 0.0 : carBaseline.getCo2Grams();
        return new JourneySearchResult(List.copyOf(allOptions), carBaselineCo2Grams);
    }

    /**
     * Finds the nearest stop that has outgoing edges (i.e. is usable as a routing origin)
     * to the given geographic coordinates.
     */
    private StopDistance findNearestConnectedStop(
            double lat,
            double lon,
            Map<String, Stop> stops,
            Map<String, List<StopEdge>> adjacencyList
    ) {
        //Iterates through all stops in the graph, calculating the distance from the provided coordinates to each stop that has outgoing edges.
        StopDistance nearest = null;
        for (Map.Entry<String, Stop> entry : stops.entrySet()) {
            String stopId = entry.getKey();
            if (adjacencyList.getOrDefault(stopId, List.of()).isEmpty()) {
                continue;
            }
            // The distance is calculated using the Haversine formula, which provides the great-circle distance between two points on the Earth's surface based on their latitudes and longitudes.
            Stop stop = entry.getValue();
            double distanceKm = emissionsCalculator.haversineDistanceKm(lat, lon, stop.getLatitude(), stop.getLongitude());
            if (nearest == null || distanceKm < nearest.distanceKm()) {
                nearest = new StopDistance(stopId, distanceKm);
            }
        }
        return nearest;
    }

    /**
     * Builds a car baseline journey option when the origin is a geographic coordinate rather than a stop.
     */
    private JourneyOption buildCarBaselineFromCoords(
            double originLat, //latitude for mapping the provided geographic coordinates to a virtual origin stop for the car baseline calculation.
            double originLon, //longitude for mapping the provided geographic coordinates to a virtual origin stop for the car baseline calculation.
            String destinationStopId,//destination stop ID for the car baseline calculation.
            Map<String, Stop> stops
    ) {
        Stop destination = stops.get(destinationStopId);
        if (destination == null) {
            return null;
        }

        double straightLineKm = emissionsCalculator.haversineDistanceKm(originLat, originLon, destination.getLatitude(), destination.getLongitude());//calculates straight line distance using haversine
        double roadDistanceKm = straightLineKm * 1.25;//times 1.25 to estimate road distance.
        double speedKmPerHour = roadDistanceKm <= 15.0 ? 35.0 : 75.0;//estimates average speed based on distance, with slower speeds for shorter trips to reflect urban driving conditions.
        int durationSeconds = (int) Math.round((roadDistanceKm / speedKmPerHour) * 3600.0);
        double co2Grams = emissionsCalculator.estimateCarCo2Grams(roadDistanceKm);

        //A virtual origin stop is created to represent the provided geographic coordinates, allowing the car baseline to be constructed as a direct route from this virtual origin to the destination stop.
        Stop virtualOrigin = new Stop("custom-location", "Your Location", originLat, originLon);
        return new JourneyOption(
                JourneyOptionType.CAR_BASELINE,
                List.of(virtualOrigin, destination),
                Math.max(0, durationSeconds),
                0,
                co2Grams,
                999_999.0,
                false,
                "Driving baseline for comparison",
                "Car"
        );
    }

    //Scores and recommends the best journey options based on a weighted combination of total duration, number of transfers, and CO2 emissions.
    private List<JourneyOption> scoreAndRecommend(List<JourneyOption> options) {
        if (options.isEmpty()) {
            return List.of();
        }

        //Calculates the minimum and maximum values for total duration, transfers, and CO2 emissions across all options to use for normalization in the scoring process.
        int minTime = options.stream().mapToInt(JourneyOption::getTotalDurationSeconds).min().orElse(0);
        int maxTime = options.stream().mapToInt(JourneyOption::getTotalDurationSeconds).max().orElse(0);
        int minTransfers = options.stream().mapToInt(JourneyOption::getTransfers).min().orElse(0);
        int maxTransfers = options.stream().mapToInt(JourneyOption::getTransfers).max().orElse(0);
        double minCo2 = options.stream().mapToDouble(JourneyOption::getCo2Grams).min().orElse(0.0);
        double maxCo2 = options.stream().mapToDouble(JourneyOption::getCo2Grams).max().orElse(0.0);

        //Each option is scored based on a weighted sum of its normalized total duration, transfers, and CO2 emissions.
        // The option with the lowest score is identified as the best option and marked as recommended, with a reason provided for the recommendation.
        List<ScoredJourney> scoredJourneys = new ArrayList<>();
        int bestIndex = 0;
        double bestScore = Double.MAX_VALUE;

        //Iterates through each journey option, normalizing its attributes and calculating a composite score based on the defined weights.
        // The best option is tracked based on the lowest score.
        for (int i = 0; i < options.size(); i++) {
            JourneyOption option = options.get(i);

            // Normalizes the total duration, transfers, and CO2 emissions for the option to a 0-1 scale based on the minimum and maximum values across all options.
            // This normalization allows for a fair comparison between options that may have different scales for these attributes.
            double normTime = normalize(option.getTotalDurationSeconds(), minTime, maxTime);
            double normTransfers = normalize(option.getTransfers(), minTransfers, maxTransfers);
            double normCo2 = normalize(option.getCo2Grams(), minCo2, maxCo2);

            // Calculates a composite score for the option using the defined weights for time, transfers, and CO2 emissions.
            // A lower score indicates a better option based on the weighted criteria.
            double score = (TIME_WEIGHT * normTime)
                    + (TRANSFERS_WEIGHT * normTransfers)
                    + (CO2_WEIGHT * normCo2);

            // The scored option is added to the list of scored journeys, and the best option is tracked based on the lowest score.
            scoredJourneys.add(new ScoredJourney(option, score));
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        //The best option is identified and marked as recommended, with a reason provided for the recommendation based on which attributes it excels in (e.g., fastest, fewest transfers, lowest CO2).
        JourneyOption winner = options.get(bestIndex);
        String recommendationReason = recommendationReason(winner, minTime, minTransfers, minCo2);

        //The list of journey options is updated to mark the best option as recommended and to include the recommendation reason in its description.
        List<JourneyOption> updated = new ArrayList<>();
        for (int i = 0; i < scoredJourneys.size(); i++) {
            ScoredJourney scoredJourney = scoredJourneys.get(i);
            JourneyOption option = scoredJourney.option();
            boolean recommended = i == bestIndex;

            // Each option is reconstructed with the recommendation status and reason included in its description,
            // allowing the frontend to display this information to users when presenting the journey options.
            updated.add(new JourneyOption(
                    option.getType(),
                    option.getStops(),
                    option.getTotalDurationSeconds(),
                    option.getTransfers(),
                    option.getCo2Grams(),
                    scoredJourney.score(),
                    recommended,
                    recommended ? recommendationReason : "Alternative route option",
                    option.getModeSummary()
            ));
        }

        return List.copyOf(updated);
    }

    //Determines the reason for recommending a particular journey option based on its attributes compared to the minimum values across all options.
    private String recommendationReason(
            JourneyOption option,
            int minTime,
            int minTransfers,
            double minCo2
    ) {
        // Checks if the option is the fastest, has the fewest transfers, or has the lowest CO2 emissions compared to the minimum values.
        boolean fastest = option.getTotalDurationSeconds() == minTime;
        boolean fewestTransfers = option.getTransfers() == minTransfers;
        boolean lowestCo2 = Double.compare(option.getCo2Grams(), minCo2) == 0;

        // Based on which attributes the option excels in, a reason for the recommendation is constructed.
        int wins = 0;
        if (fastest) {
            wins++;
        }
        if (fewestTransfers) {
            wins++;
        }
        if (lowestCo2) {
            wins++;
        }

        if (wins > 1) {
            return "Best balance of time + transfers + CO2";
        }
        if (fastest) {
            return "Fastest option";
        }
        if (fewestTransfers) {
            return "Fewest transfers";
        }
        if (lowestCo2) {
            return "Lowest CO2";
        }

        return "Best balance of time + transfers + CO2";
    }

    //Builds a car baseline journey option based on the effective origin and destination stops, estimating travel time and CO2 emissions for driving directly between them.
    private JourneyOption buildCarBaseline(String originStopId, String destinationStopId, Map<String, Stop> stops) {
        Stop origin = stops.get(originStopId);
        Stop destination = stops.get(destinationStopId);
        if (origin == null || destination == null) {
            return null;
        }

        // The straight-line distance between the origin and destination is calculated using the Haversine formula, 
        // which provides an estimate of the direct distance between the two points on the Earth's surface.
        double straightLineKm = emissionsCalculator.haversineDistanceKm(
                origin.getLatitude(),
                origin.getLongitude(),
                destination.getLatitude(),
                destination.getLongitude()
        );

        // The road distance is estimated by multiplying the straight-line distance by a factor (1.25) to account for the fact that roads do not follow a perfectly straight path between two points.
        double roadDistanceKm = straightLineKm * 1.25;
        double speedKmPerHour = roadDistanceKm <= 15.0 ? 35.0 : 75.0;

        // The travel time for the car baseline is estimated based on the road distance and an average speed, with slower speeds assumed for shorter trips to reflect urban driving conditions.
        int durationSeconds = (int) Math.round((roadDistanceKm / speedKmPerHour) * 3600.0);
        double co2Grams = emissionsCalculator.estimateCarCo2Grams(roadDistanceKm);

        // A journey option is created for the car baseline, with the type set to CAR_BASELINE and a description indicating that it is a driving baseline for comparison.
        // The mode summary is set to "Car" to clearly indicate the mode of transport for this option.
        return new JourneyOption(
                JourneyOptionType.CAR_BASELINE,
                List.of(origin, destination),
                Math.max(0, durationSeconds),
                0,
                co2Grams,
                999_999.0,
                false,
                "Driving baseline for comparison",
                "Car"
        );
    }

    //Adds a public transport journey option to the list of options to be scored, based on the provided path result and access times to the origin and destination stops.
    private void addPublicTransportOption(
            List<JourneyOption> target,
            Set<String> seenSignatures,
            PathResult pathResult,
            Map<String, Stop> stopsById,
            Map<String, List<StopEdge>> adjacencyList,
            String requestedOriginStopId,
            String requestedDestinationStopId,
            int originAccessSeconds,
            int destinationAccessSeconds
    ) {
        //If the path result is null or contains no stops, it is not added as a journey option.
        //This check ensures that only valid routes with at least one stop are considered for scoring and recommendation.
        if (pathResult == null || pathResult.stopIds().isEmpty()) {
            return;
        }

        String signature = String.join("->", pathResult.stopIds());
        if (!seenSignatures.add(signature)) {
            return;
        }

        List<Stop> stopList = new ArrayList<>();
        if (!requestedOriginStopId.equals(pathResult.stopIds().get(0))) {
            Stop requestedOrigin = stopsById.get(requestedOriginStopId);
            if (requestedOrigin != null) {
                stopList.add(requestedOrigin);
            }
        }

        for (String stopId : pathResult.stopIds()) {
            Stop stop = stopsById.get(stopId);
            if (stop != null) {
                stopList.add(stop);
            }
        }

        if (!requestedDestinationStopId.equals(pathResult.stopIds().get(pathResult.stopIds().size() - 1))) {
            Stop requestedDestination = stopsById.get(requestedDestinationStopId);
            if (requestedDestination != null) {
                stopList.add(requestedDestination);
            }
        }

        if (stopList.isEmpty()) {
            return;
        }

        // The modes of transport used in the path are extracted to determine the mode summary and to calculate CO2 emissions based on the transport modes and distances traveled.
        List<TransportMode> pathModes = extractPathModes(pathResult.stopIds(), adjacencyList);
        List<TransportMode> multimodalModes = new ArrayList<>();
        if (originAccessSeconds > 0) {
            multimodalModes.add(TransportMode.WALK);
        }
        multimodalModes.addAll(pathModes);
        if (destinationAccessSeconds > 0) {
            multimodalModes.add(TransportMode.WALK);
        }

        // The walking distance for accessing the stops is calculated from the access times,
        //  and the CO2 emissions for the public transport portion of the journey are calculated based on the modes used and distances traveled.
        double accessWalkDistanceKm = walkingDistanceKmFromSeconds(originAccessSeconds + destinationAccessSeconds);
        double co2Grams = computePublicTransportCo2(pathResult.stopIds(), stopsById, adjacencyList)
            + emissionsCalculator.estimateEdgeCo2Grams(accessWalkDistanceKm, TransportMode.WALK);
        int transfers = countVehicleTransfers(multimodalModes);
        int durationSeconds = Math.max(60, pathResult.totalDurationSeconds() + originAccessSeconds + destinationAccessSeconds);
        String modeSummary = buildModeSummary(multimodalModes);

        //A new journey option is created for the public transport route, with the type set to PUBLIC_TRANSPORT and a description indicating that it is a public transport option.
        // The total duration includes the travel time from the path result plus any access times
        target.add(new JourneyOption(
                JourneyOptionType.PUBLIC_TRANSPORT,
                List.copyOf(stopList),
                durationSeconds,
                transfers,
                co2Grams,
                0.0,
                false,
                "",
                modeSummary
        ));
    }

    // Resolves the effective routing anchors for the journey search, which may involve finding nearby stops with outgoing edges if the requested origin or destination stops do not have direct routes between them.
    private RoutingAnchors resolveRoutingAnchors(
            String requestedOriginStopId,
            String requestedDestinationStopId,
            Map<String, Stop> stopsById,
            Map<String, List<StopEdge>> adjacencyList
    ) {
        // First, it checks if there is a direct route between the requested origin and destination stops using Dijkstra's algorithm.
        // If a direct route exists, it can be used as the routing anchors without needing to consider nearby stops.
        PathResult direct = dijkstra(
                requestedOriginStopId,
                requestedDestinationStopId,
                adjacencyList,
                edge -> edge.getTravelTimeSeconds()
        );
        if (direct != null) {
            return new RoutingAnchors(requestedOriginStopId, requestedDestinationStopId, 0, 0);
        }

        // If no direct route exists, the method looks for nearby stops with outgoing edges for both the origin and destination.
        // It uses the nearestStopCandidates method to find a list of candidate stops within a certain
        List<String> originCandidates = nearestStopCandidates(
                requestedOriginStopId,// The requested origin stop ID is used to find nearby candidate stops that have outgoing edges, which can serve as potential routing anchors for the origin.
                stopsById,
                adjacencyList,
                ROUTING_CANDIDATE_LIMIT,
                ROUTING_CANDIDATE_RADIUS_KM,
                true
        );
        // Similarly, it finds nearby candidate stops for the destination, but does not require them to have outgoing edges since they can serve as routing anchors for the destination.
        List<String> destinationCandidates = nearestStopCandidates(
                requestedDestinationStopId,// The requested destination stop ID is used to find nearby candidate stops that have outgoing edges, which can serve as potential routing anchors for the destination.
                stopsById,
                adjacencyList,
                ROUTING_CANDIDATE_LIMIT,
                ROUTING_CANDIDATE_RADIUS_KM,
                false
        );

        // The method then iterates through all combinations of origin and destination candidate stops, using Dijkstra's algorithm to find the best route between each pair of candidates.
        RoutingAnchors best = null;
        int bestTotalSeconds = Integer.MAX_VALUE;

        // For each pair of origin and destination candidate stops, it calculates the total travel time including any access times to the requested origin and destination stops.
        for (String originCandidateId : originCandidates) {
            for (String destinationCandidateId : destinationCandidates) {
                PathResult candidatePath = dijkstra(
                        originCandidateId,
                        destinationCandidateId,
                        adjacencyList,
                        edge -> edge.getTravelTimeSeconds()
                );
                if (candidatePath == null) {
                    continue;
                }

                // The access times from the requested origin to the origin candidate and from the destination candidate to the requested destination are estimated based on walking time, which is calculated using the distance between the stops and a defined walking speed.
                int originAccessSeconds = estimateWalkingSecondsBetweenStops(
                        requestedOriginStopId,
                        originCandidateId,
                        stopsById
                );
                // The total travel time for the candidate route is calculated by adding the travel time from the path result and the access times. The best route is tracked based on the lowest total travel time.
                int destinationAccessSeconds = estimateWalkingSecondsBetweenStops(
                        destinationCandidateId,
                        requestedDestinationStopId,
                        stopsById
                );

                // If the total travel time for the candidate route is less than the best total travel time found so far, the routing anchors are updated to use this candidate route.
                int totalSeconds = candidatePath.totalDurationSeconds() + originAccessSeconds + destinationAccessSeconds;
                if (totalSeconds < bestTotalSeconds) {
                    bestTotalSeconds = totalSeconds;
                    best = new RoutingAnchors(
                            originCandidateId,
                            destinationCandidateId,
                            originAccessSeconds,
                            destinationAccessSeconds
                    );
                }
            }
        }

        return best;
    }

    //helper method to find nearby stop candidates for routing anchors, based on distance and whether they have outgoing edges.
    private List<String> nearestStopCandidates(
            String requestedStopId,
            Map<String, Stop> stopsById,
            Map<String, List<StopEdge>> adjacencyList,
            int limit,
            double radiusKm,
            boolean requireOutgoingEdges
    ) {
        // The method retrieves the requested stop from the stops map using the provided stop ID. If the requested stop does not exist, an empty list is returned.
        Stop requestedStop = stopsById.get(requestedStopId);
        if (requestedStop == null) {
            return List.of();
        }

        //returns a list of nearby stops sorted by distance.
        List<StopDistance> candidates = new ArrayList<>();
        for (Map.Entry<String, Stop> entry : stopsById.entrySet()) {
            String candidateStopId = entry.getKey();
            Stop candidateStop = entry.getValue();

            // If requireOutgoingEdges is true, the method checks if the candidate stop has outgoing edges in the adjacency list. If it does not, the candidate is skipped.
            if (requireOutgoingEdges && adjacencyList.getOrDefault(candidateStopId, List.of()).isEmpty()) {
                continue;
            }

            // The distance from the requested stop to the candidate stop is calculated using the Haversine formula,
            // which provides the great-circle distance between two points on the Earth's surface based on their latitudes and longitudes.
            double distanceKm = emissionsCalculator.haversineDistanceKm(
                    requestedStop.getLatitude(),
                    requestedStop.getLongitude(),
                    candidateStop.getLatitude(),
                    candidateStop.getLongitude()
            );

            // If the distance is within the specified radius or if the candidate stop is the same as the requested stop, it is added to the list of candidates.
            // This allows for nearby stops to be considered as potential routing anchors, while also ensuring that the originally requested stop is included as a candidate.
            if (distanceKm <= radiusKm || candidateStopId.equals(requestedStopId)) {
                candidates.add(new StopDistance(candidateStopId, distanceKm));
            }
        }

        // The list of candidates is sorted by distance in ascending order, so that the closest stops are prioritized when selecting routing anchors.
        candidates.sort(Comparator.comparingDouble(StopDistance::distanceKm));

        // The method then constructs a list of stop IDs for the nearest candidates, up to the specified limit.
        // If the requested stop ID is not already included in the list of candidates, it is added at the beginning of the list to ensure that it is considered as a potential routing anchor.
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < candidates.size() && i < limit; i++) {
            ids.add(candidates.get(i).stopId());
        }
        if (!ids.contains(requestedStopId)) {
            ids.add(0, requestedStopId);
        }

        return ids;
    }

    // Estimates the walking time in seconds between two stops based on their geographic coordinates and a defined walking speed.
    // If either stop does not exist, or if the stops are the same, the walking time is estimated as 0 seconds.
    private int estimateWalkingSecondsBetweenStops(
            String fromStopId,
            String toStopId,
            Map<String, Stop> stopsById
    ) {
        if (fromStopId.equals(toStopId)) {
            return 0;
        }

        // The method retrieves the "from" and "to" stops from the stops map using the provided stop IDs. If either stop does not exist, the method returns 0 seconds for the walking time.
        Stop from = stopsById.get(fromStopId);
        Stop to = stopsById.get(toStopId);
        if (from == null || to == null) {
            return 0;
        }

        // The distance between the two stops is calculated using the Haversine formula,
        // which provides the great-circle distance between two points on the Earth's surface based on their latitudes and longitudes.
        double distanceKm = emissionsCalculator.haversineDistanceKm(
                from.getLatitude(),
                from.getLongitude(),
                to.getLatitude(),
                to.getLongitude()
        );

        return walkingSeconds(distanceKm);
    }

    // Converts a walking distance in kilometers to an estimated walking time in seconds based on a defined walking speed.
    private int walkingSeconds(double distanceKm) {
        return (int) Math.max(0, Math.round((distanceKm / WALK_SPEED_KMH) * 3600.0));
    }

    // Converts a walking time in seconds to an estimated walking distance in kilometers based on a defined walking speed.
    private double walkingDistanceKmFromSeconds(int seconds) {
        return Math.max(0.0, (seconds / 3600.0) * WALK_SPEED_KMH);
    }

    // Extracts the modes of transport used in a path based on the edges between consecutive stops in the path.
    // If no edge is found between two stops, or if the edge does not have a defined transport mode, it defaults to BUS.
    private List<TransportMode> extractPathModes(List<String> stopIds, Map<String, List<StopEdge>> adjacencyList) {
        List<TransportMode> modes = new ArrayList<>();
        for (int i = 1; i < stopIds.size(); i++) {
            String fromId = stopIds.get(i - 1);
            String toId = stopIds.get(i);
            StopEdge selectedEdge = selectBestEdge(fromId, toId, adjacencyList);// The best edge between the two stops is selected based on the shortest travel time, its default is bus if no edge or transport mode is found.
            modes.add(selectedEdge == null || selectedEdge.getTransportMode() == null
                    ? TransportMode.BUS
                    : selectedEdge.getTransportMode());
        }
        return modes;
    }

    // Counts the number of vehicle transfers in a list of transport modes by filtering out walking segments and counting changes in motorized transport modes.
    private int countVehicleTransfers(List<TransportMode> modes) {
        List<TransportMode> motorized = new ArrayList<>();
        for (TransportMode mode : modes) {
            if (mode != TransportMode.WALK) {
                motorized.add(mode);
            }
        }

        // If there are zero or one motorized segments, there are no transfers, so the method returns 0.
        if (motorized.size() <= 1) {
            return 0;
        }

        // The method iterates through the list of motorized transport modes and counts the number of times the mode changes from one segment to the next,
        // which indicates a transfer between different vehicles or lines.
        int transfers = 0;
        TransportMode previous = motorized.get(0);
        for (int i = 1; i < motorized.size(); i++) {
            TransportMode current = motorized.get(i);
            if (current != previous) {
                transfers++;
            }
            previous = current;
        }

        return transfers;
    }

    // Builds a summary of the transport modes used in a journey by filtering out consecutive duplicate modes and formatting the mode names for display.
    private String buildModeSummary(List<TransportMode> modes) {
        List<String> labels = new ArrayList<>();
        TransportMode previous = null;

        // The method iterates through the list of transport modes and adds a formatted label for each mode to the summary,
        // while skipping consecutive duplicate modes to create a cleaner and more concise summary of the journey's transport modes.
        for (TransportMode mode : modes) {
            if (mode == null || mode == previous) {
                continue;
            }
            labels.add(formatMode(mode));
            previous = mode;
        }

        if (labels.isEmpty()) {
            return "Public transport";
        }
        return String.join(" → ", labels);  //the formatted mode labels are joined with an arrow symbol to create a clear and visually distinct summary of the transport modes
    }

    //Formats a transport mode enum value into a user-friendly string label for display in the mode summary of a journey option.
    private String formatMode(TransportMode mode) {
        return switch (mode) {
            case WALK -> "Walk";
            case BUS -> "Bus";
            case TRAIN -> "Train";
            case TRAM, LUAS -> "Tram";
            case BIKE -> "Bike";
            case CAR -> "Car";
        };
    }

    // Computes the total CO2 emissions for a journey using public transport by iterating through the list of stop IDs,
    // calculating the distance between consecutive stops, and estimating the CO2 emissions for each segment.
    private double computePublicTransportCo2(
            List<String> stopIds,
            Map<String, Stop> stopsById,
            Map<String, List<StopEdge>> adjacencyList
    ) {
        // The method iterates through the list of stop IDs in the path, calculating the distance between each pair of consecutive stops using the Haversine formula.
        double total = 0.0;
        for (int i = 1; i < stopIds.size(); i++) {
            String fromId = stopIds.get(i - 1);
            String toId = stopIds.get(i);

            Stop from = stopsById.get(fromId);
            Stop to = stopsById.get(toId);
            if (from == null || to == null) {
                continue;
            }

            // The distance between the two stops is calculated using the Haversine formula, which provides the great-circle distance between two points on the Earth's surface based on their latitudes and longitudes.
            double distanceKm = emissionsCalculator.haversineDistanceKm(
                    from.getLatitude(),
                    from.getLongitude(),
                    to.getLatitude(),
                    to.getLongitude()
            );

            // The best edge between the two stops is selected based on the shortest travel time, and the CO2 emissions for that segment are estimated based on the distance and the transport mode of the selected edge.
            StopEdge selectedEdge = selectBestEdge(fromId, toId, adjacencyList);
            total += emissionsCalculator.estimateEdgeCo2Grams(
                    distanceKm,
                    selectedEdge == null ? null : selectedEdge.getTransportMode()
            );
        }
        return total;
    }

    // Selects the best edge between two stops based on the shortest travel time, which is used to determine the transport mode for CO2 estimation in the public transport route.
    private StopEdge selectBestEdge(String fromId, String toId, Map<String, List<StopEdge>> adjacencyList) {
        StopEdge best = null;
        for (StopEdge edge : adjacencyList.getOrDefault(fromId, List.of())) {
            if (!toId.equals(edge.getToStopId())) {
                continue;
            }
            if (best == null || edge.getTravelTimeSeconds() < best.getTravelTimeSeconds()) {
                best = edge;
            }
        }
        return best;
    }

    // Normalizes a value to a 0-1 scale based on the provided minimum and maximum values, which is used in the scoring process to compare journey options across different attributes.
    private double normalize(double value, double min, double max) {
        if (Double.compare(max, min) == 0) {
            return 0.0;
        }
        return (value - min) / (max - min);
    }

    // Implements Dijkstra's algorithm to find the shortest path between two stops based on a custom edge weight function.
    private PathResult dijkstra(
            String originStopId,
            String destinationStopId,
            Map<String, List<StopEdge>> adjacencyList,
            EdgeWeightFunction edgeWeightFunction
    ) {
        // The method uses a priority queue to explore the stops in order of their known shortest distance from the origin,
        // and it maintains a map of distances and previous stops to reconstruct the path once the destination is reached.
        Map<String, Integer> distance = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<NodeCost> queue = new PriorityQueue<>(Comparator.comparingInt(NodeCost::cost));

        // The distance to the origin stop is initialized to 0, and the origin stop is added to the priority queue to start the algorithm.
        distance.put(originStopId, 0);
        queue.add(new NodeCost(originStopId, 0));

        // The algorithm continues to explore stops until the priority queue is empty, checking each stop's neighbors and updating distances and previous stops as shorter paths are found.
        while (!queue.isEmpty()) {
            NodeCost current = queue.poll();
            int knownDistance = distance.getOrDefault(current.stopId(), Integer.MAX_VALUE);
            if (current.cost() > knownDistance) {
                continue;
            }

            // If the destination stop is reached, the algorithm breaks out of the loop to reconstruct the path. If the destination is not reachable, the method will eventually return null.
            if (current.stopId().equals(destinationStopId)) {
                break;
            }

            // The method iterates through the neighbors of the current stop, calculating the candidate distance to each neighbor based on the current distance and the weight of the edge connecting them.
            List<StopEdge> neighbours = adjacencyList.getOrDefault(current.stopId(), Collections.emptyList());
            for (StopEdge edge : neighbours) {
                int edgeWeight = Math.max(1, edgeWeightFunction.weight(edge));
                int candidate = current.cost() + edgeWeight;

                int existing = distance.getOrDefault(edge.getToStopId(), Integer.MAX_VALUE);
                if (candidate < existing) {
                    distance.put(edge.getToStopId(), candidate);
                    previous.put(edge.getToStopId(), current.stopId());
                    queue.add(new NodeCost(edge.getToStopId(), candidate));
                }
            }
        }

        // After the algorithm completes, the method checks if the destination stop has a known distance. If it does not, it returns null to indicate that no path exists between the origin and destination.
        if (!distance.containsKey(destinationStopId)) {
            return null;
        }

        // If a path exists, the method reconstructs the path by backtracking from the destination stop to the origin stop using the previous stops map, and it calculates the total duration of the path based on the edge weights.
        LinkedList<String> path = new LinkedList<>();
        String cursor = destinationStopId;
        path.addFirst(cursor);
        while (previous.containsKey(cursor)) {
            cursor = previous.get(cursor);
            path.addFirst(cursor);
        }

        if (!path.getFirst().equals(originStopId)) {
            return null;
        }

        int actualDuration = computeActualDuration(path, adjacencyList);
        return new PathResult(path, actualDuration);
    }

    private int computeActualDuration(List<String> path, Map<String, List<StopEdge>> adjacencyList) {
        int total = 0;
        for (int i = 1; i < path.size(); i++) {
            String from = path.get(i - 1);
            String to = path.get(i);

            int best = Integer.MAX_VALUE;
            for (StopEdge edge : adjacencyList.getOrDefault(from, List.of())) {
                if (to.equals(edge.getToStopId())) {
                    best = Math.min(best, edge.getTravelTimeSeconds());
                }
            }

            if (best == Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            total += best;
        }

        return total;
    }

    private interface EdgeWeightFunction {
        int weight(StopEdge edge);
    }

    private record NodeCost(String stopId, int cost) {
    }

    private record PathResult(List<String> stopIds, int totalDurationSeconds) {
    }

        private record RoutingAnchors(
            String routingOriginStopId,
            String routingDestinationStopId,
            int originAccessSeconds,
            int destinationAccessSeconds
        ) {
        }

        private record StopDistance(String stopId, double distanceKm) {
        }

    private record ScoredJourney(JourneyOption option, double score) {
    }
}
