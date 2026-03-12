package com.routesense.application.port;

import com.routesense.domain.model.ScheduledConnection;
import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;

import java.util.List;
import java.util.Map;

// Repository interface for accessing the stop graph and schedule data.

public interface StopGraphRepository {
    // Returns a map of stop ID -> Stop object, containing all stops in the system.
    Map<String, Stop> getStops();

    // Returns the adjacency list representing the stop graph: for each stop ID, a list of outgoing edges.
    Map<String, List<StopEdge>> getAdjacencyList();

    // Returns the full schedule: for each stop, a list of scheduled departures sorted by departure time.
    // Used by the schedule-aware Dijkstra to find the next real bus after a given arrival time.
    Map<String, List<ScheduledConnection>> getSchedule();

    // Returns a map of GTFS routeId → route short name (e.g. "405", "DART").
    // Used to label journey legs with the human-readable service name.
    Map<String, String> getRouteShortNames();

    // Returns shape geometry for train routes: routeId → ordered list of [lat, lon] points.
    // Used to draw accurate train polylines on the map instead of straight lines between stops.
    Map<String, List<double[]>> getRouteShapes();
}
