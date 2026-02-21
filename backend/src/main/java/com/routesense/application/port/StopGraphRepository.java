package com.routesense.application.port;

import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;

import java.util.List;
import java.util.Map;

/**
 * Port for reading the in-memory stop graph.
 */
public interface StopGraphRepository {
    Map<String, Stop> getStops();

    Map<String, List<StopEdge>> getAdjacencyList();
}
