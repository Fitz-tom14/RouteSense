package com.routesense.application.usecase;

import com.routesense.application.port.StopGraphRepository;
import com.routesense.domain.model.JourneyOption;
import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;
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

    private static final int BALANCED_TRANSFER_PENALTY_SECONDS = 300;

    private final StopGraphRepository stopGraphRepository;

    public SearchJourneyUseCase(StopGraphRepository stopGraphRepository) {
        this.stopGraphRepository = stopGraphRepository;
    }

    public List<JourneyOption> execute(String originStopId, String destinationStopId) {
        if (originStopId == null || originStopId.isBlank() || destinationStopId == null || destinationStopId.isBlank()) {
            return List.of();
        }

        Map<String, Stop> stops = stopGraphRepository.getStops();
        Map<String, List<StopEdge>> adjacencyList = stopGraphRepository.getAdjacencyList();

        if (!stops.containsKey(originStopId) || !stops.containsKey(destinationStopId)) {
            return List.of();
        }

        PathResult fastest = dijkstra(originStopId, destinationStopId, adjacencyList, edge -> edge.getTravelTimeSeconds());
        PathResult fewestTransfers = dijkstra(originStopId, destinationStopId, adjacencyList, edge -> 1);
        PathResult balanced = dijkstra(
                originStopId,
                destinationStopId,
                adjacencyList,
                edge -> edge.getTravelTimeSeconds() + BALANCED_TRANSFER_PENALTY_SECONDS
        );

        List<JourneyOption> result = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();

        addOption(result, seenSignatures, fastest, stops, false, BALANCED_TRANSFER_PENALTY_SECONDS);
        addOption(result, seenSignatures, fewestTransfers, stops, false, BALANCED_TRANSFER_PENALTY_SECONDS);
        addOption(result, seenSignatures, balanced, stops, true, BALANCED_TRANSFER_PENALTY_SECONDS);

        result.sort(Comparator.comparingInt(JourneyOption::getTotalDurationSeconds)
                .thenComparingInt(JourneyOption::getTransfers));

        boolean hasRecommended = result.stream().anyMatch(JourneyOption::isRecommended);
        if (!hasRecommended && !result.isEmpty()) {
            JourneyOption best = computeBalancedRecommendation(result);
            result = markRecommended(result, best);
        }

        return result;
    }

    private JourneyOption computeBalancedRecommendation(List<JourneyOption> options) {
        JourneyOption recommended = options.get(0);
        double bestScore = recommended.getTotalDurationSeconds()
                + (recommended.getTransfers() * (double) BALANCED_TRANSFER_PENALTY_SECONDS);

        for (JourneyOption option : options) {
            double score = option.getTotalDurationSeconds()
                    + (option.getTransfers() * (double) BALANCED_TRANSFER_PENALTY_SECONDS);
            if (score < bestScore) {
                bestScore = score;
                recommended = option;
            }
        }

        return recommended;
    }

    private List<JourneyOption> markRecommended(List<JourneyOption> options, JourneyOption recommended) {
        List<JourneyOption> updated = new ArrayList<>();
        for (JourneyOption option : options) {
            updated.add(new JourneyOption(
                    option.getStops(),
                    option.getTotalDurationSeconds(),
                    option.getTransfers(),
                    option.getScore(),
                    option == recommended
            ));
        }
        return updated;
    }

    private void addOption(
            List<JourneyOption> target,
            Set<String> seenSignatures,
            PathResult pathResult,
            Map<String, Stop> stopsById,
            boolean recommended,
            int transferPenaltySeconds
    ) {
        if (pathResult == null || pathResult.stopIds().isEmpty()) {
            return;
        }

        String signature = String.join("->", pathResult.stopIds());
        if (!seenSignatures.add(signature)) {
            if (recommended) {
                for (int i = 0; i < target.size(); i++) {
                    JourneyOption existing = target.get(i);
                    if (signature.equals(buildSignature(existing.getStops()))) {
                        target.set(i, new JourneyOption(
                                existing.getStops(),
                                existing.getTotalDurationSeconds(),
                                existing.getTransfers(),
                                existing.getScore(),
                                true
                        ));
                        break;
                    }
                }
            }
            return;
        }

        List<Stop> stopList = new ArrayList<>();
        for (String stopId : pathResult.stopIds()) {
            Stop stop = stopsById.get(stopId);
            if (stop != null) {
                stopList.add(stop);
            }
        }

        if (stopList.isEmpty()) {
            return;
        }

        int transfers = Math.max(0, stopList.size() - 2);
        double score = pathResult.totalDurationSeconds() + (transfers * (double) transferPenaltySeconds);

        target.add(new JourneyOption(
                List.copyOf(stopList),
                pathResult.totalDurationSeconds(),
                transfers,
                score,
                recommended
        ));
    }

    private String buildSignature(List<Stop> stops) {
        List<String> ids = new ArrayList<>(stops.size());
        for (Stop stop : stops) {
            ids.add(stop.getId());
        }
        return String.join("->", ids);
    }

    private PathResult dijkstra(
            String originStopId,
            String destinationStopId,
            Map<String, List<StopEdge>> adjacencyList,
            EdgeWeightFunction edgeWeightFunction
    ) {
        Map<String, Integer> distance = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<NodeCost> queue = new PriorityQueue<>(Comparator.comparingInt(NodeCost::cost));

        distance.put(originStopId, 0);
        queue.add(new NodeCost(originStopId, 0));

        while (!queue.isEmpty()) {
            NodeCost current = queue.poll();
            int knownDistance = distance.getOrDefault(current.stopId(), Integer.MAX_VALUE);
            if (current.cost() > knownDistance) {
                continue;
            }

            if (current.stopId().equals(destinationStopId)) {
                break;
            }

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

        if (!distance.containsKey(destinationStopId)) {
            return null;
        }

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
}
