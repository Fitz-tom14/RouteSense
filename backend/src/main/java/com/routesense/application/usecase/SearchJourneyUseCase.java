package com.routesense.application.usecase;

import com.routesense.application.port.StopGraphRepository;
import com.routesense.application.service.EmissionsCalculator;
import com.routesense.domain.model.FootpathEdge;
import com.routesense.domain.model.JourneyLeg;
import com.routesense.domain.model.JourneyOption;
import com.routesense.domain.model.JourneyOptionType;
import com.routesense.domain.model.JourneySearchResult;
import com.routesense.domain.model.ScheduledConnection;
import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;
import com.routesense.domain.model.TransportMode;
import com.routesense.infrastructure.routing.OpenRouteServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
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
import java.util.stream.Collectors;

// Use case for searching journeys between two stops or locations.  This is the core of the RouteSense
// application and contains the main routing logic, including the schedule-aware Dijkstra and car baseline.
// The controller passes in the search parameters and this use case returns a list of scored JourneyOption objects for display on the route cards.

@Component
public class SearchJourneyUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchJourneyUseCase.class);

    // Scoring weights for the recommendation engine
    private static final double TIME_WEIGHT      = 0.55;
    private static final double TRANSFERS_WEIGHT = 0.25;
    private static final double CO2_WEIGHT       = 0.20;

    // 1-hour penalty per extra transfer in the fewest-transfers Dijkstra variant
    private static final int TRANSFER_SCORE_PENALTY = 3600;

    // 10-minute penalty per transfer in the fastest variant — ensures a direct bus
    // is always preferred unless a multi-transfer route saves at least 10 min per transfer
    private static final int FASTEST_TRANSFER_PENALTY_SECONDS = 600;

    // Fallback Dijkstra constants (used only when schedule data is empty)
    private static final int    BALANCED_TRANSFER_PENALTY_SECONDS = 300;
    private static final double WALK_SPEED_KMH                    = 4.8;
    private static final int    ROUTING_CANDIDATE_LIMIT            = 8;
    private static final double ROUTING_CANDIDATE_RADIUS_KM        = 20.0;

    
    private final StopGraphRepository      stopGraphRepository;
    private final EmissionsCalculator      emissionsCalculator;
    private final OpenRouteServiceClient   openRouteServiceClient;

    
    public SearchJourneyUseCase(
            StopGraphRepository    stopGraphRepository,
            EmissionsCalculator    emissionsCalculator,
            OpenRouteServiceClient openRouteServiceClient
    ) {
        this.stopGraphRepository    = stopGraphRepository;
        this.emissionsCalculator    = emissionsCalculator;
        this.openRouteServiceClient = openRouteServiceClient;
    }

    // Main entry point for searching journeys.
    // Takes origin and destination stop IDs or coordinates, departure/arrival time, and returns a list of JourneyOption objects with scoring and recommendation info.
    public JourneySearchResult execute(
            String  originStopId,
            Double  originLat,
            Double  originLon,
            String  destinationStopId,
            Double  destinationLat,
            Double  destinationLon,
            Integer departureTimeSeconds,
            Integer arriveBySeconds
    ) {
        Map<String, Stop>                      stops          = stopGraphRepository.getStops();
        Map<String, List<StopEdge>>            adjacencyList  = stopGraphRepository.getAdjacencyList();
        Map<String, List<ScheduledConnection>> schedule       = stopGraphRepository.getSchedule();
        Map<String, String>                    routeShortNames = stopGraphRepository.getRouteShortNames();
        Map<String, List<double[]>>            routeShapes    = stopGraphRepository.getRouteShapes();
        Map<String, List<FootpathEdge>>        footpaths      = stopGraphRepository.getFootpaths();

        // --- Resolve destination candidates ---
        // Build a list of destination stop IDs to try. When coordinates are given we try:
        //   1. The nearest scheduled stop within 600 m (a nearby bus stop the user might mean)
        //   2. The nearest TRAIN station within 20 km (covers rural Ireland where no bus stop
        //      exists near the pin but a train station is within reasonable distance)
        // Both candidates are tried so a user near a bus stop still gets bus results AND
        // a user far from any bus stop still gets a train route to the nearest station.
        List<String> destCandidates = new ArrayList<>();
        if (destinationLat != null && destinationLon != null && (destinationStopId == null || destinationStopId.isBlank())) {
            // Nearest scheduled bus stop within 600 m
            List<StopDistance> nearbyDest = findNearbyStops(destinationLat, destinationLon, 0.6, stops).stream()
                    .filter(sd -> !schedule.getOrDefault(sd.stopId(), List.of()).isEmpty())
                    .collect(Collectors.toList());
            if (!nearbyDest.isEmpty()) {
                destCandidates.add(nearbyDest.get(0).stopId());
            }

            // Always also try the nearest train station within 20 km
            StopDistance nearestTrainDest = findNearestTrainStop(destinationLat, destinationLon, stops, schedule, 20.0);
            if (nearestTrainDest != null && !destCandidates.contains(nearestTrainDest.stopId())) {
                destCandidates.add(nearestTrainDest.stopId());
            }

            // If no stop was found within a walkable distance (bus within 600 m or train within 2 km),
            // also add the nearest scheduled stop within 20 km. This handles pins in rural areas where
            // a distant train station was already added but the nearest bus stop is closer and more useful.
            boolean hasWalkableDest = !nearbyDest.isEmpty() ||
                    (nearestTrainDest != null && nearestTrainDest.distanceKm() <= 2.0);
            if (!hasWalkableDest) {
                List<StopDistance> farDest = findNearbyStops(destinationLat, destinationLon, 20.0, stops).stream()
                        .filter(sd -> !schedule.getOrDefault(sd.stopId(), List.of()).isEmpty())
                        .collect(Collectors.toList());
                if (!farDest.isEmpty() && !destCandidates.contains(farDest.get(0).stopId())) {
                    destCandidates.add(farDest.get(0).stopId());
                }
            }
        } else {
            if (destinationStopId != null && !destinationStopId.isBlank()) {
                destCandidates.add(destinationStopId);
            }
        }

        if (destCandidates.isEmpty()) {
            return new JourneySearchResult(List.of(), 0.0);
        }
        // Primary destination for guard checks and car baseline
        destinationStopId = destCandidates.get(0);
        LOGGER.info("DEBUG dest candidates: {}, origin coords: {},{}", destCandidates, destinationLat, destinationLon);

        // When "arrive by" is set, start searching 3 hours before the target so we find real options.
        // Otherwise fall back to the explicit departure time or the current time.
        int startTime;
        if (arriveBySeconds != null) {
            startTime = Math.max(0, arriveBySeconds - 3 * 3600);
        } else {
            startTime = departureTimeSeconds != null
                    ? departureTimeSeconds
                    : LocalTime.now().toSecondOfDay();
        }

        // --- Resolve effective origin(s) ---
        // When the user drops a map pin, consider ALL stops within 600 m as potential origins
        // (not just the single nearest). This is essential because two stops at the same
        // location can serve opposite directions — e.g. the eastbound and westbound Gleann Dara
        // stops are only ~30 m apart but serve completely different services.
        // When a stop ID was typed directly, we use only that stop.
        List<StopDistance> originCandidates;
        if (originLat != null && originLon != null && (originStopId == null || originStopId.isBlank())) {
            // Use the 3 nearest stops within 600 m.  Limiting to 3 avoids generating a flood of
            // near-identical options when many stops of the same bus line are nearby (e.g. Bus 405
            // passes through four stops all within 400 m of the pin).
            List<StopDistance> nearby = findNearbyConnectedStops(originLat, originLon, 0.6, stops, adjacencyList);
            List<StopDistance> candidates = new ArrayList<>(nearby.size() > 3 ? nearby.subList(0, 3) : nearby);

            // Also look for the nearest train station within 5 km — train stations are typically
            // further from residential pins but still reachable on foot.  Only add if not already
            // in the candidate list (avoids duplicate when the user pins right next to a station).
            StopDistance nearestTrain = findNearestTrainStop(originLat, originLon, stops, schedule, 20.0);
            if (nearestTrain != null) {
                boolean alreadyPresent = candidates.stream()
                        .anyMatch(c -> c.stopId().equals(nearestTrain.stopId()));
                if (!alreadyPresent) {
                    candidates.add(nearestTrain);
                }
            }

            // If no stop was found within a walkable distance (bus within 600 m or train within 2 km),
            // also add the nearest connected stop within 20 km as a fallback for rural origin pins.
            boolean hasWalkableOrigin = !nearby.isEmpty() ||
                    (nearestTrain != null && nearestTrain.distanceKm() <= 2.0);
            if (!hasWalkableOrigin) {
                List<StopDistance> farOrigin = findNearbyConnectedStops(originLat, originLon, 20.0, stops, adjacencyList);
                if (!farOrigin.isEmpty()) {
                    boolean alreadyPresent = candidates.stream().anyMatch(c -> c.stopId().equals(farOrigin.get(0).stopId()));
                    if (!alreadyPresent) {
                        candidates.add(farOrigin.get(0));
                    }
                }
            }

            originCandidates = candidates;
            if (originCandidates.isEmpty()) {
                return new JourneySearchResult(List.of(), 0.0);
            }
        } else {
            if (originStopId == null || originStopId.isBlank()) {
                return new JourneySearchResult(List.of(), 0.0);
            }
            originCandidates = List.of(new StopDistance(originStopId, 0.0));
        }

        // Use the nearest candidate as the "effective" origin for fallback Dijkstra and car baseline.
        StopDistance primaryOrigin = originCandidates.get(0);
        String effectiveOriginStopId  = primaryOrigin.stopId();
        int    extraOriginWalkSeconds = walkingSeconds(primaryOrigin.distanceKm());

        LOGGER.info("DEBUG origin candidates: {}, effective origin: {}", originCandidates.stream().map(StopDistance::stopId).collect(Collectors.toList()), effectiveOriginStopId);
        if (!stops.containsKey(effectiveOriginStopId) || !stops.containsKey(destinationStopId)) {
            LOGGER.info("DEBUG stop not found — effectiveOrigin in stops: {}, dest in stops: {}", stops.containsKey(effectiveOriginStopId), stops.containsKey(destinationStopId));
            return new JourneySearchResult(List.of(), 0.0);
        }

        //Find public transport options
        List<JourneyOption> optionsToScore = new ArrayList<>();
        Set<String>         seenSignatures = new HashSet<>();

        if (!schedule.isEmpty()) {
            // Try each destination candidate in order; collect routes from whichever produce results.
            for (String destId : destCandidates) {
                String scheduleDest = resolveScheduleStop(destId, schedule, stops, adjacencyList);
                if (scheduleDest == null) continue;

                // Try every nearby origin stop so we don't miss buses on the opposite side of the road.
                for (StopDistance candidate : originCandidates) {
                    int walkSecs     = walkingSeconds(candidate.distanceKm());
                    int boardingTime = startTime + walkSecs;

                    String scheduleOrigin = resolveScheduleStop(candidate.stopId(), schedule, stops, adjacencyList);
                    if (scheduleOrigin == null) continue;

                    // Primary path: real departure times from GTFS schedule
                    ScheduledPath fastest = scheduleAwareDijkstra(
                            scheduleOrigin, scheduleDest, schedule, stops, footpaths, boardingTime, FASTEST_TRANSFER_PENALTY_SECONDS);
                    ScheduledPath fewestTransfers = scheduleAwareDijkstra(
                            scheduleOrigin, scheduleDest, schedule, stops, footpaths, boardingTime, TRANSFER_SCORE_PENALTY);
                    // 3rd option: next bus 30 minutes later (catches a different service)
                    ScheduledPath laterOption = scheduleAwareDijkstra(
                            scheduleOrigin, scheduleDest, schedule, stops, footpaths, boardingTime + 1800, FASTEST_TRANSFER_PENALTY_SECONDS);
                    // If no buses found AND it is late night (after 21:00), retry from next morning.
                    boolean isLateNight = boardingTime > 21 * 3600;
                    if (fastest == null && isLateNight) {
                        fastest = scheduleAwareDijkstra(scheduleOrigin, scheduleDest, schedule, stops, footpaths, 0, FASTEST_TRANSFER_PENALTY_SECONDS);
                    }
                    if (fewestTransfers == null && isLateNight) {
                        fewestTransfers = scheduleAwareDijkstra(
                                scheduleOrigin, scheduleDest, schedule, stops, footpaths, 0, TRANSFER_SCORE_PENALTY);
                    }
                    if (laterOption == null && isLateNight) {
                        laterOption = scheduleAwareDijkstra(
                                scheduleOrigin, scheduleDest, schedule, stops, footpaths, 3600, FASTEST_TRANSFER_PENALTY_SECONDS);
                    }

                    addScheduledOption(optionsToScore, seenSignatures, fastest,        stops, walkSecs, destId, routeShapes);
                    addScheduledOption(optionsToScore, seenSignatures, fewestTransfers, stops, walkSecs, destId, routeShapes);
                    addScheduledOption(optionsToScore, seenSignatures, laterOption,     stops, walkSecs, destId, routeShapes);
                }
            }
        }

        // If schedule-aware search found nothing (e.g. no more departures today), fall back to
        // the time-unaware Dijkstra so the user always gets route options.
        if (optionsToScore.isEmpty()) {
            runFallbackDijkstra(effectiveOriginStopId, destinationStopId,
                    stops, adjacencyList, routeShortNames, extraOriginWalkSeconds, optionsToScore, seenSignatures);
        }

        List<JourneyOption> scoredPublicOptions = new ArrayList<>(scoreAndRecommend(optionsToScore));
        scoredPublicOptions.sort(Comparator.comparingInt(JourneyOption::getTotalDurationSeconds)
                .thenComparingInt(JourneyOption::getTransfers)
                .thenComparingDouble(JourneyOption::getCo2Grams));

        // When the user asked to "arrive by" a specific time, discard any routes whose estimated
        // arrival time (startTime + duration) would be after that target.
        if (arriveBySeconds != null) {
            final int deadline = arriveBySeconds;
            final int base     = startTime;
            scoredPublicOptions = scoredPublicOptions.stream()
                    .filter(o -> base + o.getTotalDurationSeconds() <= deadline)
                    .collect(Collectors.toList());
        }

        //Car baseline
        double originLatForCar = originLat  != null ? originLat  : stops.get(effectiveOriginStopId).getLatitude();
        double originLonForCar = originLon  != null ? originLon  : stops.get(effectiveOriginStopId).getLongitude();
        Stop   destination     = stops.get(destinationStopId);

        // If we have a valid destination stop, try to build a car baseline.  If the ORS call fails, the fallback is a JourneyOption with infinite duration and zero CO2 which will never be recommended.
        JourneyOption    carBaseline     = null;
        List<List<Double>> carGeometry  = null;
        if (destination != null) {
            CarBaselineResult cbr = buildCarBaseline(originLatForCar, originLonForCar, destination);
            carBaseline = cbr.option();
            carGeometry = cbr.geometry();
        }

        List<JourneyOption> allOptions = new ArrayList<>(scoredPublicOptions);
        if (carBaseline != null) {
            allOptions.add(carBaseline);
        }

        double carBaselineCo2 = carBaseline == null ? 0.0 : carBaseline.getCo2Grams();
        return new JourneySearchResult(List.copyOf(allOptions), carBaselineCo2, carGeometry);
    }

    // Builds a car baseline option using OpenRouteService for distance and duration, falling back to haversine distance if the API call fails.
    // The car baseline is used to provide context to the user — if the recommended bus route takes 45 minutes but the car baseline is 15 minutes, the user understands that the bus is slower but may still prefer it for cost or environmental reasons.  If the car baseline is close to or worse than the bus options, that strengthens the recommendation for public transport.
    // Max walking distance to count a nearby stop as "reached the destination"
    private static final double DEST_WALK_RADIUS_KM = 0.45;

    // Result of building the car baseline, including the JourneyOption and the route geometry for map display.
    private ScheduledPath scheduleAwareDijkstra(
            String originStopId,
            String destinationStopId,
            Map<String, List<ScheduledConnection>> schedule,
            Map<String, Stop> stops,
            Map<String, List<FootpathEdge>> footpaths,
            int startTimeSeconds,
            int transferPenaltySeconds
    ) {
        Stop destStop = stops.get(destinationStopId);
        double destLat = destStop != null ? destStop.getLatitude()  : Double.NaN;
        double destLon = destStop != null ? destStop.getLongitude() : Double.NaN;

        // bestScore tracks the lowest score (arrivalTime + transfers*penalty) seen
        // for each (stopId, routeId) pair so we can skip stale queue entries.
        Map<String, Integer>     bestScore = new HashMap<>();
        PriorityQueue<TripState> queue     = new PriorityQueue<>(Comparator.comparingInt(TripState::score));

        // Initial state: at the origin stop, not on any route, at the specified start time, with zero transfers and zero score.
        TripState start = new TripState(originStopId, null, startTimeSeconds, 0, 0, null, null, false);
        queue.add(start);
        bestScore.put(stateKey(originStopId, null), 0);

        TripState destinationState   = null;
        TripState bestProximityState = null;
        double    bestProximityDistKm = Double.MAX_VALUE;
        // Once we have a proximity candidate, keep looking for up to 10 min of extra
        // Dijkstra score to find a closer alighting stop (e.g. Eyre Square vs Saint Francis St).
        final int PROXIMITY_SLACK_S = 600;

        while (!queue.isEmpty()) {
            TripState current = queue.poll();

            // Skip this entry if we already found a better path to this state
            if (current.score() > bestScore.getOrDefault(stateKey(current.stopId(), current.routeId()), Integer.MAX_VALUE)) {
                continue;
            }

            // If we already have a proximity candidate and this state is much slower, stop exploring.
            if (bestProximityState != null && current.score() > bestProximityState.score() + PROXIMITY_SLACK_S) {
                break;
            }

            // Exact match always terminates immediately.
            if (current.stopId().equals(destinationStopId)) {
                destinationState = current;
                break;
            }

            // Proximity check: record the closest stop within walking distance of the destination.
            // Do NOT break here — continue so the bus can reach an even closer stop (e.g. Eyre Square
            // is closer to Ceannt Station than Saint Francis Street which comes one stop earlier).
            if (!Double.isNaN(destLat)) {
                Stop cur = stops.get(current.stopId());
                if (cur != null) {
                    double dist = emissionsCalculator.haversineDistanceKm(
                            cur.getLatitude(), cur.getLongitude(), destLat, destLon);
                    if (dist <= DEST_WALK_RADIUS_KM && dist < bestProximityDistKm) {
                        bestProximityDistKm = dist;
                        bestProximityState  = current;
                    }
                }
            }

            // Look at all buses departing from this stop
            for (ScheduledConnection conn : schedule.getOrDefault(current.stopId(), List.of())) {

                // Can only board a bus that has not yet left
                if (conn.getDepartureTimeSeconds() < current.arrivalTime()) {
                    continue;
                }

                // Direction filter: at the origin stop (no route yet, and not reached via a footpath walk),
                // skip connections that move more than 100 m further from the destination.
                // This prevents boarding a loop route in the wrong direction at the very start.
                // We skip this filter after a footpath walk so we don't over-restrict stops near
                // the origin where buses may briefly move away before continuing toward the destination.
                if (current.routeId() == null && !current.walkedHere() && !Double.isNaN(destLat)) {
                    Stop curStop  = stops.get(current.stopId());
                    Stop nextStop = stops.get(conn.getToStopId());
                    if (curStop != null && nextStop != null) {
                        double curDist  = emissionsCalculator.haversineDistanceKm(
                                curStop.getLatitude(),  curStop.getLongitude(),  destLat, destLon);
                        double nextDist = emissionsCalculator.haversineDistanceKm(
                                nextStop.getLatitude(), nextStop.getLongitude(), destLat, destLon);
                        if (nextDist > curDist + 0.1) {
                            continue; // first hop goes backwards — skip
                        }
                    }
                }

                // Boarding a different route than the one we are already on is a transfer.
                // The very first boarding (routeId == null) is not counted as a transfer.
                boolean isTransfer  = current.routeId() != null && !conn.getRouteId().equals(current.routeId());
                int     newTransfers = current.transfers() + (isTransfer ? 1 : 0);
                int     newScore     = conn.getArrivalTimeSeconds() + newTransfers * transferPenaltySeconds;

                String nextKey = stateKey(conn.getToStopId(), conn.getRouteId());
                if (newScore < bestScore.getOrDefault(nextKey, Integer.MAX_VALUE)) {
                    bestScore.put(nextKey, newScore);
                    queue.add(new TripState(
                            conn.getToStopId(),
                            conn.getRouteId(),
                            conn.getArrivalTimeSeconds(),
                            newTransfers,
                            newScore,
                            current,
                            conn,
                            false
                    ));
                }
            }

            // Footpath transfers: walk to any stop within 300 m.
            // This is what enables train → walk → bus journeys.
            // We only walk one hop (walkedHere prevents chaining walk→walk→walk).
            if (!current.walkedHere()) {
                for (FootpathEdge fp : footpaths.getOrDefault(current.stopId(), List.of())) {
                    int arriveAfterWalk = current.arrivalTime() + fp.walkSeconds();
                    int walkScore       = arriveAfterWalk + current.transfers() * transferPenaltySeconds;
                    // Keep the current routeId so that boarding transit at the new stop counts as a transfer
                    String walkKey = stateKey(fp.toStopId(), current.routeId());
                    if (walkScore < bestScore.getOrDefault(walkKey, Integer.MAX_VALUE)) {
                        bestScore.put(walkKey, walkScore);
                        queue.add(new TripState(
                                fp.toStopId(),
                                current.routeId(),  // maintain route context — boarding here will count as a transfer
                                arriveAfterWalk,
                                current.transfers(),
                                walkScore,
                                current,
                                null,               // null connection = walk leg (no scheduled vehicle)
                                true                // walkedHere = true prevents further walk chaining
                        ));
                    }
                }
            }
        }

        // Fall back to the closest proximity stop if no exact match was found.
        if (destinationState == null) {
            destinationState = bestProximityState;
        }
        if (destinationState == null) {
            return null;
        }

        return reconstructScheduledPath(destinationState, startTimeSeconds);
    }

    // Walks back through the parent-pointer chain to build the list of legs.
    // Each step is either a transit leg (connection != null) or a walk leg (connection == null).
    private ScheduledPath reconstructScheduledPath(TripState destination, int startTime) {
        LinkedList<PathLeg> legs = new LinkedList<>();
        TripState current = destination;

        while (current.parent() != null) {
            if (current.connection() != null) {
                // Transit leg — use the scheduled connection for times and service name
                ScheduledConnection conn = current.connection();
                legs.addFirst(new PathLeg(
                        current.parent().stopId(),
                        current.stopId(),
                        conn.getRouteId(),
                        conn.getRouteShortName(),
                        conn.getMode(),
                        conn.getDepartureTimeSeconds(),
                        conn.getArrivalTimeSeconds()
                ));
            } else {
                // Walk leg — the user walks between two nearby stops (footpath transfer)
                legs.addFirst(new PathLeg(
                        current.parent().stopId(),
                        current.stopId(),
                        null,
                        null,
                        TransportMode.WALK,
                        current.parent().arrivalTime(),
                        current.arrivalTime()
                ));
            }
            current = current.parent();
        }

        if (legs.isEmpty()) {
            return null;
        }

        int totalDuration = destination.arrivalTime() - startTime;
        return new ScheduledPath(new ArrayList<>(legs), Math.max(0, totalDuration), destination.transfers());
    }

    private void addScheduledOption(
            List<JourneyOption>         target,
            Set<String>                 seenSignatures,
            ScheduledPath               path,
            Map<String, Stop>           stops,
            int                         extraOriginWalkSeconds,
            String                      requestedDestStopId,
            Map<String, List<double[]>> routeShapes
    ) {
        if (path == null || path.legs().isEmpty()) {
            return;
        }

        // Build stop list for display
        List<String> stopIds = new ArrayList<>();
        stopIds.add(path.legs().get(0).fromStopId());
        for (PathLeg leg : path.legs()) {
            stopIds.add(leg.toStopId());
        }

        // Deduplicate: two routes are equivalent when they use the same ordered sequence of
        // services AND arrive at the same time.  Appending the final arrival time prevents
        // e.g. a "Bus 405 at 06:38" and a "Bus 405 at 07:20" from being collapsed, while
        // collapsing a "Bus 405 from Gleann Dara (06:38)" and a "Bus 405 from Bishop O'Donnell
        // Road (06:37)" that are the same service arriving at the same destination time.
        List<String> uniqueServices = new ArrayList<>();
        String prev = null;
        for (PathLeg leg : path.legs()) {
            String sn = leg.routeShortName() != null && !leg.routeShortName().isBlank()
                    ? leg.routeShortName() : leg.fromStopId();
            if (!sn.equals(prev)) {
                uniqueServices.add(sn);
                prev = sn;
            }
        }
        PathLeg lastLeg = path.legs().get(path.legs().size() - 1);
        String signature = String.join("->", uniqueServices) + "@" + lastLeg.arrivalSeconds();
        if (signature.isBlank() || signature.startsWith("@")) {
            signature = String.join("->", stopIds);
        }
        if (!seenSignatures.add(signature)) {
            return;
        }

        // Collect Stop objects in order
        List<Stop> stopList = new ArrayList<>();
        for (String id : stopIds) {
            Stop s = stops.get(id);
            if (s != null) stopList.add(s);
        }
        if (stopList.isEmpty()) {
            return;
        }

        // Build per-leg detail (service name, times) for display on the route card
        List<JourneyLeg> journeyLegs = new ArrayList<>();
        for (PathLeg leg : path.legs()) {
            Stop from = stops.get(leg.fromStopId());
            Stop to   = stops.get(leg.toStopId());
            String fromName   = from != null ? from.getName() : leg.fromStopId();
            String toName     = to   != null ? to.getName()   : leg.toStopId();
            String modeLabel  = formatMode(leg.mode());
            // Trains have no meaningful route number in Irish GTFS, so use the departure time
            // as the service identifier (e.g. "Train dep. 14:30"). This also ensures that
            // two different trains on the same journey are kept as separate legs.
            String service;
            if (leg.mode() == TransportMode.TRAIN) {
                service = "Train dep. " + formatTime(leg.departureSeconds());
            } else if (leg.routeShortName() != null && !leg.routeShortName().isBlank()) {
                service = modeLabel + " " + leg.routeShortName();
            } else {
                service = modeLabel;
            }
            List<double[]> shapePoints = null;
            if (from != null && to != null) {
                shapePoints = extractShapeSegment(leg.routeId(), routeShapes,
                        from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude());
            }
            journeyLegs.add(new JourneyLeg(
                    service,
                    fromName,
                    toName,
                    formatTime(leg.departureSeconds()),
                    formatTime(leg.arrivalSeconds()),
                    modeLabel,
                    shapePoints
            ));
        }

        // If the path terminated at a nearby stop rather than the exact destination, add a short
        // walk leg so the user sees "→ Galway (Ceannt)" rather than "→ Saint Francis Street".
        int finalWalkSeconds = 0;
        if (requestedDestStopId != null && !stopIds.isEmpty()) {
            String lastStopId = stopIds.get(stopIds.size() - 1);
            if (!lastStopId.equals(requestedDestStopId)) {
                Stop lastStop = stops.get(lastStopId);
                Stop destStop = stops.get(requestedDestStopId);
                if (lastStop != null && destStop != null) {
                    double walkKm = emissionsCalculator.haversineDistanceKm(
                            lastStop.getLatitude(), lastStop.getLongitude(),
                            destStop.getLatitude(), destStop.getLongitude());
                    if (walkKm <= DEST_WALK_RADIUS_KM) {
                        finalWalkSeconds = walkingSeconds(walkKm);
                        int busArrival = lastLeg.arrivalSeconds();
                        journeyLegs.add(new JourneyLeg(
                                "Walk",
                                lastStop.getName(),
                                destStop.getName(),
                                formatTime(busArrival),
                                formatTime(busArrival + finalWalkSeconds),
                                "Walk",
                                null
                        ));
                        stopList.add(destStop);
                    }
                }
            }
        }

        double co2Grams    = calculateCo2ForLegs(path.legs(), stops);
        int    duration    = Math.max(60, path.totalDurationSeconds() + extraOriginWalkSeconds + finalWalkSeconds);
        String modeSummary = buildModeSummaryFromLegs(path.legs(), extraOriginWalkSeconds > 0);

        target.add(new JourneyOption(
                JourneyOptionType.PUBLIC_TRANSPORT,
                List.copyOf(stopList),
                duration,
                path.transfers(),
                co2Grams,
                0.0,
                false,
                "",
                modeSummary,
                List.copyOf(journeyLegs)
        ));
    }

    // Formats seconds-since-midnight as "HH:mm". Handles times past midnight.
    private String formatTime(int totalSeconds) {
        int h = (totalSeconds % 86400) / 3600;
        int m = (totalSeconds % 3600) / 60;
        return String.format("%02d:%02d", h, m);
    }

    // Sums CO2 emissions across all legs using haversine distance and emissions factor. 
    private double calculateCo2ForLegs(List<PathLeg> legs, Map<String, Stop> stops) {
        double total = 0.0;
        for (PathLeg leg : legs) {
            Stop from = stops.get(leg.fromStopId());
            Stop to   = stops.get(leg.toStopId());
            if (from == null || to == null) continue;
            double distKm = emissionsCalculator.haversineDistanceKm(
                    from.getLatitude(), from.getLongitude(),
                    to.getLatitude(),   to.getLongitude());
            total += emissionsCalculator.estimateEdgeCo2Grams(distKm, leg.mode());
        }
        return total;
    }

    // Builds a user-friendly mode summary like "Walk → Bus 405 → Train" from the leg sequence.
    // Intermediate footpath walk legs are skipped — they are short transfers, not main modes.
    // Only the initial walk to the first stop is included (walkAtStart flag).
    private String buildModeSummaryFromLegs(List<PathLeg> legs, boolean walkAtStart) {
        List<String> parts = new ArrayList<>();
        if (walkAtStart) {
            parts.add("Walk");
        }
        String prevLabel = null;
        for (PathLeg leg : legs) {
            // Skip intermediate walk legs (footpath transfers) — they clutter the summary
            if (leg.mode() == TransportMode.WALK) continue;
            String label = (leg.routeShortName() != null && !leg.routeShortName().isBlank())
                    ? formatMode(leg.mode()) + " " + leg.routeShortName()
                    : formatMode(leg.mode());
            if (!label.equals(prevLabel)) {
                parts.add(label);
                prevLabel = label;
            }
        }
        return parts.isEmpty() ? "Public transport" : String.join(" → ", parts);
    }

   // Builds a car baseline option using OpenRouteService for distance and duration, falling back to haversine distance if the API call fails.
   // The car baseline is used to provide context to the user — if the recommended bus route takes 45 minutes but the car baseline is 15 minutes,
   // the user understands that the bus is slower but may still prefer it for cost or environmental reasons.
   // If the car baseline is close to or worse than the bus options, that strengthens the recommendation for public transport.
    private CarBaselineResult buildCarBaseline(double originLat, double originLon, Stop destination) {
        double distanceKm;
        int    durationSeconds;
        List<List<Double>> geometry = null;

        OpenRouteServiceClient.CarRoute orsRoute = openRouteServiceClient
                .getDrivingRoute(originLat, originLon, destination.getLatitude(), destination.getLongitude())
                .orElse(null);

        if (orsRoute != null) {
            distanceKm      = orsRoute.distanceKm();
            durationSeconds = orsRoute.durationSeconds();
            geometry        = orsRoute.geometry(); // road-following polyline from ORS
        } else {
            // Haversine fallback: straight-line * 1.25 to approximate road distance
            double straightKm = emissionsCalculator.haversineDistanceKm(
                    originLat, originLon, destination.getLatitude(), destination.getLongitude());
            distanceKm     = straightKm * 1.25;
            double speedKmh = distanceKm <= 15.0 ? 35.0 : 75.0;
            durationSeconds = (int) Math.round((distanceKm / speedKmh) * 3600.0);
        }

        double co2Grams      = emissionsCalculator.estimateCarCo2Grams(distanceKm);
        Stop   virtualOrigin = new Stop("custom-location", "Your Location", originLat, originLon);

        JourneyOption option = new JourneyOption(
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
        return new CarBaselineResult(option, geometry);
    }

    private record CarBaselineResult(JourneyOption option, List<List<Double>> geometry) {}


    // Resolves the "effective" origin and destination stops for the fallback Dijkstra search.
    // If the user dropped a pin, this picks the nearest stop with schedule data as the effective stop for routing and scoring purposes.
    // If the user typed a stop ID directly, that is used as the effective stop without modification.

    // Runs the original three-variant Dijkstra search when no schedule data is loaded.
    private void runFallbackDijkstra(
            String                     effectiveOriginStopId,
            String                     destinationStopId,
            Map<String, Stop>          stops,
            Map<String, List<StopEdge>> adjacencyList,
            Map<String, String>        routeShortNames,
            int                        extraOriginWalkSeconds,
            List<JourneyOption>        optionsToScore,
            Set<String>                seenSignatures
    ) {
        RoutingAnchors anchors = resolveRoutingAnchors(
                effectiveOriginStopId, destinationStopId, stops, adjacencyList);
        if (anchors == null) {
            return;
        }

        PathResult fastest = dijkstra(anchors.routingOriginStopId(), anchors.routingDestinationStopId(),
                adjacencyList, edge -> edge.getTravelTimeSeconds());
        PathResult fewestTransfers = dijkstra(anchors.routingOriginStopId(), anchors.routingDestinationStopId(),
                adjacencyList, edge -> 1);
        PathResult balanced = dijkstra(anchors.routingOriginStopId(), anchors.routingDestinationStopId(),
                adjacencyList, edge -> edge.getTravelTimeSeconds() + BALANCED_TRANSFER_PENALTY_SECONDS);

        int totalOriginAccess = anchors.originAccessSeconds() + extraOriginWalkSeconds;
        addPublicTransportOption(optionsToScore, seenSignatures, fastest,        stops, adjacencyList, routeShortNames,
                effectiveOriginStopId, destinationStopId, totalOriginAccess, anchors.destinationAccessSeconds());
        addPublicTransportOption(optionsToScore, seenSignatures, fewestTransfers, stops, adjacencyList, routeShortNames,
                effectiveOriginStopId, destinationStopId, totalOriginAccess, anchors.destinationAccessSeconds());
        addPublicTransportOption(optionsToScore, seenSignatures, balanced,       stops, adjacencyList, routeShortNames,
                effectiveOriginStopId, destinationStopId, totalOriginAccess, anchors.destinationAccessSeconds());
    }


    //scoring recommendation logic: assign a score to each option based on normalized time, transfers, and CO2, then mark the best one as recommended with a reason.

    private List<JourneyOption> scoreAndRecommend(List<JourneyOption> options) {
        if (options.isEmpty()) {
            return List.of();
        }

        int    minTime      = options.stream().mapToInt(JourneyOption::getTotalDurationSeconds).min().orElse(0);
        int    maxTime      = options.stream().mapToInt(JourneyOption::getTotalDurationSeconds).max().orElse(0);
        int    minTransfers = options.stream().mapToInt(JourneyOption::getTransfers).min().orElse(0);
        int    maxTransfers = options.stream().mapToInt(JourneyOption::getTransfers).max().orElse(0);
        double minCo2       = options.stream().mapToDouble(JourneyOption::getCo2Grams).min().orElse(0.0);
        double maxCo2       = options.stream().mapToDouble(JourneyOption::getCo2Grams).max().orElse(0.0);

        List<ScoredJourney> scored     = new ArrayList<>();
        int                 bestIndex  = 0;
        double              bestScore  = Double.MAX_VALUE;

        for (int i = 0; i < options.size(); i++) {
            JourneyOption option = options.get(i);
            double normTime      = normalize(option.getTotalDurationSeconds(), minTime, maxTime);
            double normTransfers = normalize(option.getTransfers(), minTransfers, maxTransfers);
            double normCo2       = normalize(option.getCo2Grams(), minCo2, maxCo2);
            double score         = TIME_WEIGHT * normTime + TRANSFERS_WEIGHT * normTransfers + CO2_WEIGHT * normCo2;

            scored.add(new ScoredJourney(option, score));
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        JourneyOption winner = options.get(bestIndex);
        String        reason = recommendationReason(winner, minTime, minTransfers, minCo2);

        List<JourneyOption> updated = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            ScoredJourney sj          = scored.get(i);
            boolean       recommended = i == bestIndex;
            updated.add(new JourneyOption(
                    sj.option().getType(),
                    sj.option().getStops(),
                    sj.option().getTotalDurationSeconds(),
                    sj.option().getTransfers(),
                    sj.option().getCo2Grams(),
                    sj.score(),
                    recommended,
                    recommended ? reason : "Alternative route option",
                    sj.option().getModeSummary(),
                    sj.option().getLegs()
            ));
        }

        return List.copyOf(updated);
    }

    private String recommendationReason(JourneyOption option, int minTime, int minTransfers, double minCo2) {
        boolean fastest         = option.getTotalDurationSeconds() == minTime;
        boolean fewestTransfers = option.getTransfers() == minTransfers;
        boolean lowestCo2       = Double.compare(option.getCo2Grams(), minCo2) == 0;

        int wins = (fastest ? 1 : 0) + (fewestTransfers ? 1 : 0) + (lowestCo2 ? 1 : 0);
        if (wins > 1)       return "Best balance of time + transfers + CO2";
        if (fastest)        return "Fastest option";
        if (fewestTransfers) return "Fewest transfers";
        if (lowestCo2)      return "Lowest CO2";
        return "Best balance of time + transfers + CO2";
    }

    // Normalizes a value to a 0.0-1.0 range based on the provided min and max, where 0.0 is best and 1.0 is worst.
    // If all values are the same (min == max), returns 0.0 to avoid division by zero and treat them as equally good.
    private void addPublicTransportOption(
            List<JourneyOption>          target,
            Set<String>                  seenSignatures,
            PathResult                   pathResult,
            Map<String, Stop>            stopsById,
            Map<String, List<StopEdge>>  adjacencyList,
            Map<String, String>          routeShortNames,
            String                       requestedOriginStopId,
            String                       requestedDestinationStopId,
            int                          originAccessSeconds,
            int                          destinationAccessSeconds
    ) {
        if (pathResult == null || pathResult.stopIds().isEmpty()) {
            return;
        }

        String signature = String.join("->", pathResult.stopIds());
        if (!seenSignatures.add(signature)) {
            return;
        }

        List<Stop> stopList = new ArrayList<>();
        if (!requestedOriginStopId.equals(pathResult.stopIds().get(0))) {
            Stop s = stopsById.get(requestedOriginStopId);
            if (s != null) stopList.add(s);
        }
        for (String stopId : pathResult.stopIds()) {
            Stop s = stopsById.get(stopId);
            if (s != null) stopList.add(s);
        }
        if (!requestedDestinationStopId.equals(pathResult.stopIds().get(pathResult.stopIds().size() - 1))) {
            Stop s = stopsById.get(requestedDestinationStopId);
            if (s != null) stopList.add(s);
        }

        if (stopList.isEmpty()) return;

        List<TransportMode> pathModes       = extractPathModes(pathResult.stopIds(), adjacencyList);
        List<TransportMode> multimodalModes = new ArrayList<>();
        if (originAccessSeconds      > 0) multimodalModes.add(TransportMode.WALK);
        multimodalModes.addAll(pathModes);
        if (destinationAccessSeconds > 0) multimodalModes.add(TransportMode.WALK);

        double accessWalkDistKm = walkingDistanceKmFromSeconds(originAccessSeconds + destinationAccessSeconds);
        double co2Grams  = computePublicTransportCo2(pathResult.stopIds(), stopsById, adjacencyList)
                + emissionsCalculator.estimateEdgeCo2Grams(accessWalkDistKm, TransportMode.WALK);
        int    duration  = Math.max(60, pathResult.totalDurationSeconds() + originAccessSeconds + destinationAccessSeconds);
        String modeSummary = buildModeSummary(multimodalModes);

        // Build per-leg detail by grouping consecutive hops on the same route
        List<JourneyLeg> journeyLegs = buildLegsFromPath(pathResult.stopIds(), stopsById, adjacencyList, routeShortNames);

        // Transfers = number of distinct service segments minus 1 (each leg change is one transfer)
        int transfers = Math.max(0, journeyLegs.size() - 1);

        target.add(new JourneyOption(
                JourneyOptionType.PUBLIC_TRANSPORT,
                List.copyOf(stopList),
                duration,
                transfers,
                co2Grams,
                0.0,
                false,
                "",
                modeSummary,
                List.copyOf(journeyLegs)
        ));
    }

   // Builds the list of JourneyLegs for display by walking through the path of stop IDs and looking up the corresponding routes and modes from the adjacency list.
   // Consecutive hops on the same route are collapsed into a single leg.
    private List<JourneyLeg> buildLegsFromPath(
            List<String>                stopIds,
            Map<String, Stop>           stopsById,
            Map<String, List<StopEdge>> adjacencyList,
            Map<String, String>         routeShortNames
    ) {
        List<JourneyLeg> legs = new ArrayList<>();
        if (stopIds.size() < 2) return legs;

        int i = 0;
        while (i < stopIds.size() - 1) {
            StopEdge firstEdge    = selectBestEdge(stopIds.get(i), stopIds.get(i + 1), adjacencyList);
            String   currentRoute = firstEdge != null ? firstEdge.getRouteId() : null;
            String   fromId       = stopIds.get(i);
            int      j            = i + 1;

            // Extend the leg as long as we stay on the same route
            while (j < stopIds.size() - 1) {
                StopEdge nextEdge  = selectBestEdge(stopIds.get(j), stopIds.get(j + 1), adjacencyList);
                String   nextRoute = nextEdge != null ? nextEdge.getRouteId() : null;
                if (!java.util.Objects.equals(currentRoute, nextRoute)) break;
                j++;
            }

            String toId      = stopIds.get(j);
            String shortName = currentRoute != null ? routeShortNames.getOrDefault(currentRoute, "") : "";
            TransportMode mode = firstEdge != null && firstEdge.getTransportMode() != null
                    ? firstEdge.getTransportMode() : TransportMode.BUS;
            String modeLabel = formatMode(mode);
            String service   = !shortName.isBlank() ? modeLabel + " " + shortName : modeLabel;

            Stop from = stopsById.get(fromId);
            Stop to   = stopsById.get(toId);
            legs.add(new JourneyLeg(
                    service,
                    from != null ? from.getName() : fromId,
                    to   != null ? to.getName()   : toId,
                    "", "",
                    modeLabel,
                    null
            ));
            i = j;
        }
        return legs;
    }

    private RoutingAnchors resolveRoutingAnchors(
            String requestedOriginStopId,
            String requestedDestinationStopId,
            Map<String, Stop> stopsById,
            Map<String, List<StopEdge>> adjacencyList
    ) {
        PathResult direct = dijkstra(requestedOriginStopId, requestedDestinationStopId,
                adjacencyList, edge -> edge.getTravelTimeSeconds());
        if (direct != null) {
            return new RoutingAnchors(requestedOriginStopId, requestedDestinationStopId, 0, 0);
        }

        List<String> originCandidates = nearestStopCandidates(
                requestedOriginStopId, stopsById, adjacencyList, ROUTING_CANDIDATE_LIMIT, ROUTING_CANDIDATE_RADIUS_KM, true);
        List<String> destinationCandidates = nearestStopCandidates(
                requestedDestinationStopId, stopsById, adjacencyList, ROUTING_CANDIDATE_LIMIT, ROUTING_CANDIDATE_RADIUS_KM, false);

        RoutingAnchors best            = null;
        int            bestTotalSeconds = Integer.MAX_VALUE;

        for (String originId : originCandidates) {
            for (String destId : destinationCandidates) {
                PathResult candidate = dijkstra(originId, destId, adjacencyList, edge -> edge.getTravelTimeSeconds());
                if (candidate == null) continue;

                int originAccess = estimateWalkingSecondsBetweenStops(requestedOriginStopId, originId, stopsById);
                int destAccess   = estimateWalkingSecondsBetweenStops(destId, requestedDestinationStopId, stopsById);
                int total        = candidate.totalDurationSeconds() + originAccess + destAccess;

                if (total < bestTotalSeconds) {
                    bestTotalSeconds = total;
                    best = new RoutingAnchors(originId, destId, originAccess, destAccess);
                }
            }
        }

        return best;
    }

    private List<String> nearestStopCandidates(
            String requestedStopId,
            Map<String, Stop> stopsById,
            Map<String, List<StopEdge>> adjacencyList,
            int limit,
            double radiusKm,
            boolean requireOutgoingEdges
    ) {
        Stop requestedStop = stopsById.get(requestedStopId);
        if (requestedStop == null) return List.of();

        List<StopDistance> candidates = new ArrayList<>();
        for (Map.Entry<String, Stop> entry : stopsById.entrySet()) {
            String candidateId = entry.getKey();
            if (requireOutgoingEdges && adjacencyList.getOrDefault(candidateId, List.of()).isEmpty()) continue;

            double distKm = emissionsCalculator.haversineDistanceKm(
                    requestedStop.getLatitude(), requestedStop.getLongitude(),
                    entry.getValue().getLatitude(), entry.getValue().getLongitude());

            if (distKm <= radiusKm || candidateId.equals(requestedStopId)) {
                candidates.add(new StopDistance(candidateId, distKm));
            }
        }

        candidates.sort(Comparator.comparingDouble(StopDistance::distanceKm));
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < candidates.size() && i < limit; i++) {
            ids.add(candidates.get(i).stopId());
        }
        if (!ids.contains(requestedStopId)) {
            ids.add(0, requestedStopId);
        }
        return ids;
    }

   
    // Resolves the effective stop ID to use for routing when the user input does not directly correspond to a stop with schedule data.
    // If the user typed a stop ID that has schedule data, that is used directly.  
    // If the user typed a stop ID that has no schedule data, find the nearest stop with schedule data and use that instead (e.g. "Saint Francis Street" → "Galway Ceannt").  If the user dropped a pin, find the nearest stop with schedule data to that location and use it as the effective stop for routing and scoring purposes.
    private String resolveScheduleStop(
            String stopId,
            Map<String, List<ScheduledConnection>> schedule,
            Map<String, Stop> stops,
            Map<String, List<StopEdge>> adjacencyList
    ) {
        if (stopId == null) return null;
        if (!schedule.getOrDefault(stopId, List.of()).isEmpty()) return stopId;

        Stop base = stops.get(stopId);
        if (base == null) return null;

        String bestId = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<String, Stop> entry : stops.entrySet()) {
            if (schedule.getOrDefault(entry.getKey(), List.of()).isEmpty()) continue;
            double dist = emissionsCalculator.haversineDistanceKm(
                    base.getLatitude(), base.getLongitude(),
                    entry.getValue().getLatitude(), entry.getValue().getLongitude());
            if (dist < 0.5 && dist < bestDist) {
                bestDist = dist;
                bestId   = entry.getKey();
            }
        }
        return bestId != null ? bestId : stopId; // fall back to original if nothing nearby
    }

    private StopDistance findNearestConnectedStop(double lat, double lon, Map<String, Stop> stops, Map<String, List<StopEdge>> adjacencyList) {
        StopDistance nearest = null;
        for (Map.Entry<String, Stop> entry : stops.entrySet()) {
            String stopId = entry.getKey();
            if (adjacencyList.getOrDefault(stopId, List.of()).isEmpty()) continue;
            double distKm = emissionsCalculator.haversineDistanceKm(
                    lat, lon, entry.getValue().getLatitude(), entry.getValue().getLongitude());
            if (nearest == null || distKm < nearest.distanceKm()) {
                nearest = new StopDistance(stopId, distKm);
            }
        }
        return nearest;
    }

    // Similar to findNearestConnectedStop but only considers stops that have train service in the schedule, since those are more likely to be useful for intercity journeys where the user dropped a pin near a station.
    private StopDistance findNearestTrainStop(
            double lat, double lon,
            Map<String, Stop> stops,
            Map<String, List<ScheduledConnection>> schedule,
            double maxKm
    ) {
        StopDistance nearest = null;
        for (Map.Entry<String, List<ScheduledConnection>> entry : schedule.entrySet()) {
            boolean hasTrain = entry.getValue().stream()
                    .anyMatch(c -> c.getMode() == TransportMode.TRAIN);
            if (!hasTrain) continue;

            Stop stop = stops.get(entry.getKey());
            if (stop == null) continue;

            double distKm = emissionsCalculator.haversineDistanceKm(lat, lon, stop.getLatitude(), stop.getLongitude());
            if (distKm <= maxKm && (nearest == null || distKm < nearest.distanceKm())) {
                nearest = new StopDistance(entry.getKey(), distKm);
            }
        }
        return nearest;
    }

    // Extracts the segment of the route shape that corresponds to the leg between (fromLat, fromLon) and (toLat, toLon) by finding the nearest points on the shape to the start and end locations.
    // This is used for displaying a map polyline of each leg of the journey.
    private List<double[]> extractShapeSegment(
            String routeId,
            Map<String, List<double[]>> routeShapes,
            double fromLat, double fromLon,
            double toLat,   double toLon
    ) {
        if (routeId == null) return null;
        List<double[]> shape = routeShapes.get(routeId);
        if (shape == null || shape.size() < 2) return null;

        int fromIdx = nearestShapeIndex(shape, fromLat, fromLon);
        int toIdx   = nearestShapeIndex(shape, toLat, toLon);

        int lo = Math.min(fromIdx, toIdx);
        int hi = Math.max(fromIdx, toIdx);
        return shape.subList(lo, hi + 1);
    }

    private int nearestShapeIndex(List<double[]> shape, double lat, double lon) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < shape.size(); i++) {
            double[] pt = shape.get(i);
            double d = emissionsCalculator.haversineDistanceKm(lat, lon, pt[0], pt[1]);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    // Returns all connected stops within maxKm of (lat,lon), sorted nearest-first.
    private List<StopDistance> findNearbyStops(
            double lat, double lon, double maxKm,
            Map<String, Stop> stops
    ) {
        List<StopDistance> result = new ArrayList<>();
        for (Map.Entry<String, Stop> entry : stops.entrySet()) {
            double distKm = emissionsCalculator.haversineDistanceKm(
                    lat, lon, entry.getValue().getLatitude(), entry.getValue().getLongitude());
            if (distKm <= maxKm) {
                result.add(new StopDistance(entry.getKey(), distKm));
            }
        }
        result.sort(Comparator.comparingDouble(StopDistance::distanceKm));
        return result;
    }

    private List<StopDistance> findNearbyConnectedStops(
            double lat, double lon, double maxKm,
            Map<String, Stop> stops,
            Map<String, List<StopEdge>> adjacencyList
    ) {
        List<StopDistance> result = new ArrayList<>();
        for (Map.Entry<String, Stop> entry : stops.entrySet()) {
            String stopId = entry.getKey();
            if (adjacencyList.getOrDefault(stopId, List.of()).isEmpty()) continue;
            double distKm = emissionsCalculator.haversineDistanceKm(
                    lat, lon, entry.getValue().getLatitude(), entry.getValue().getLongitude());
            if (distKm <= maxKm) {
                result.add(new StopDistance(stopId, distKm));
            }
        }
        result.sort(Comparator.comparingDouble(StopDistance::distanceKm));
        return result;
    }

    private int estimateWalkingSecondsBetweenStops(String fromId, String toId, Map<String, Stop> stopsById) {
        if (fromId.equals(toId)) return 0;
        Stop from = stopsById.get(fromId);
        Stop to   = stopsById.get(toId);
        if (from == null || to == null) return 0;
        return walkingSeconds(emissionsCalculator.haversineDistanceKm(
                from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude()));
    }

    private int walkingSeconds(double distanceKm) {
        return (int) Math.max(0, Math.round((distanceKm / WALK_SPEED_KMH) * 3600.0));
    }

    private double walkingDistanceKmFromSeconds(int seconds) {
        return Math.max(0.0, (seconds / 3600.0) * WALK_SPEED_KMH);
    }

    private List<TransportMode> extractPathModes(List<String> stopIds, Map<String, List<StopEdge>> adjacencyList) {
        List<TransportMode> modes = new ArrayList<>();
        for (int i = 1; i < stopIds.size(); i++) {
            StopEdge edge = selectBestEdge(stopIds.get(i - 1), stopIds.get(i), adjacencyList);
            modes.add(edge == null || edge.getTransportMode() == null ? TransportMode.BUS : edge.getTransportMode());
        }
        return modes;
    }

    private String buildModeSummary(List<TransportMode> modes) {
        List<String> labels = new ArrayList<>();
        TransportMode prev = null;
        for (TransportMode mode : modes) {
            if (mode == null || mode == prev) continue;
            labels.add(formatMode(mode));
            prev = mode;
        }
        return labels.isEmpty() ? "Public transport" : String.join(" → ", labels);
    }

    private String formatMode(TransportMode mode) {
        return switch (mode) {
            case WALK        -> "Walk";
            case BUS         -> "Bus";
            case TRAIN       -> "Train";
            case BIKE        -> "Bike";
            case CAR         -> "Car";
        };
    }

    private double computePublicTransportCo2(
            List<String> stopIds,
            Map<String, Stop> stopsById,
            Map<String, List<StopEdge>> adjacencyList
    ) {
        double total = 0.0;
        for (int i = 1; i < stopIds.size(); i++) {
            Stop from = stopsById.get(stopIds.get(i - 1));
            Stop to   = stopsById.get(stopIds.get(i));
            if (from == null || to == null) continue;
            double distKm = emissionsCalculator.haversineDistanceKm(
                    from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude());
            StopEdge edge = selectBestEdge(stopIds.get(i - 1), stopIds.get(i), adjacencyList);
            total += emissionsCalculator.estimateEdgeCo2Grams(distKm, edge == null ? null : edge.getTransportMode());
        }
        return total;
    }

    private StopEdge selectBestEdge(String fromId, String toId, Map<String, List<StopEdge>> adjacencyList) {
        StopEdge best = null;
        for (StopEdge edge : adjacencyList.getOrDefault(fromId, List.of())) {
            if (!toId.equals(edge.getToStopId())) continue;
            if (best == null || edge.getTravelTimeSeconds() < best.getTravelTimeSeconds()) best = edge;
        }
        return best;
    }

    private double normalize(double value, double min, double max) {
        return Double.compare(max, min) == 0 ? 0.0 : (value - min) / (max - min);
    }

    private PathResult dijkstra(
            String originId,
            String destinationId,
            Map<String, List<StopEdge>> adjacencyList,
            EdgeWeightFunction weightFn
    ) {
        Map<String, Integer>     distance = new HashMap<>();
        Map<String, String>      previous = new HashMap<>();
        PriorityQueue<NodeCost>  queue    = new PriorityQueue<>(Comparator.comparingInt(NodeCost::cost));

        distance.put(originId, 0);
        queue.add(new NodeCost(originId, 0));

        while (!queue.isEmpty()) {
            NodeCost current = queue.poll();
            if (current.cost() > distance.getOrDefault(current.stopId(), Integer.MAX_VALUE)) continue;
            if (current.stopId().equals(destinationId)) break;

            for (StopEdge edge : adjacencyList.getOrDefault(current.stopId(), Collections.emptyList())) {
                int candidate = current.cost() + Math.max(1, weightFn.weight(edge));
                if (candidate < distance.getOrDefault(edge.getToStopId(), Integer.MAX_VALUE)) {
                    distance.put(edge.getToStopId(), candidate);
                    previous.put(edge.getToStopId(), current.stopId());
                    queue.add(new NodeCost(edge.getToStopId(), candidate));
                }
            }
        }

        if (!distance.containsKey(destinationId)) return null;

        LinkedList<String> path = new LinkedList<>();
        String cursor = destinationId;
        path.addFirst(cursor);
        while (previous.containsKey(cursor)) {
            cursor = previous.get(cursor);
            path.addFirst(cursor);
        }
        if (!path.getFirst().equals(originId)) return null;

        return new PathResult(path, computeActualDuration(path, adjacencyList));
    }

    private int computeActualDuration(List<String> path, Map<String, List<StopEdge>> adjacencyList) {
        int total = 0;
        for (int i = 1; i < path.size(); i++) {
            int best = Integer.MAX_VALUE;
            for (StopEdge edge : adjacencyList.getOrDefault(path.get(i - 1), List.of())) {
                if (path.get(i).equals(edge.getToStopId())) {
                    best = Math.min(best, edge.getTravelTimeSeconds());
                }
            }
            if (best == Integer.MAX_VALUE) return Integer.MAX_VALUE;
            total += best;
        }
        return total;
    }

    private static String stateKey(String stopId, String routeId) {
        return stopId + ":" + (routeId != null ? routeId : "");
    }

    // Functional interface for providing edge weights to the Dijkstra search, allowing us to easily switch between different optimization criteria (fastest, fewest transfers, balanced).

    private interface EdgeWeightFunction {
        int weight(StopEdge edge);
    }

    // State node for the schedule-aware Dijkstra priority queue.
    // walkedHere = true means we arrived at this stop via a footpath walk (prevents walk→walk chaining).
    private record TripState(
            String              stopId,
            String              routeId,
            int                 arrivalTime,
            int                 transfers,
            int                 score,
            TripState           parent,
            ScheduledConnection connection,
            boolean             walkedHere
    ) {}

    // One transit leg: board at fromStopId on a named route, alight at toStopId. 
    private record PathLeg(
            String        fromStopId,
            String        toStopId,
            String        routeId,
            String        routeShortName,
            TransportMode mode,
            int           departureSeconds,
            int           arrivalSeconds
    ) {}

    // A complete journey found by scheduleAwareDijkstra.
    private record ScheduledPath(
            List<PathLeg> legs,
            int           totalDurationSeconds,
            int           transfers
    ) {}

    // Fallback Dijkstra support types
    private record NodeCost(String stopId, int cost) {}
    private record PathResult(List<String> stopIds, int totalDurationSeconds) {}
    private record RoutingAnchors(
            String routingOriginStopId,
            String routingDestinationStopId,
            int    originAccessSeconds,
            int    destinationAccessSeconds
    ) {}
    private record StopDistance(String stopId, double distanceKm) {}
    private record ScoredJourney(JourneyOption option, double score) {}
}
