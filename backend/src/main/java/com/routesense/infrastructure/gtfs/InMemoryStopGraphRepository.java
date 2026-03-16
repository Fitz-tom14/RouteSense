package com.routesense.infrastructure.gtfs;

import com.routesense.application.port.StopGraphRepository;
import com.routesense.domain.model.FootpathEdge;
import com.routesense.domain.model.ScheduledConnection;
import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// In-memory implementation of StopGraphRepository that loads GTFS data at startup and serves it from memory.
//This is the main repository used by the journey search use case to access stop and schedule data.
@Component
public class InMemoryStopGraphRepository implements StopGraphRepository {

    private final GtfsGraphLoader gtfsGraphLoader;

    public InMemoryStopGraphRepository(GtfsGraphLoader gtfsGraphLoader) {
        this.gtfsGraphLoader = gtfsGraphLoader;
    }

    // Helper method to determine the transport mode for a stop based on its outbound edges.  
    // Defaults to BUS if no edges or unknown route types.
    @Override
    public Map<String, Stop> getStops() {
        return gtfsGraphLoader.getStops();
    }

    // The adjacency list is a map of stop ID → list of outgoing edges, where each edge contains the destination stop ID, route ID, and average travel time.
    @Override
    public Map<String, List<StopEdge>> getAdjacencyList() {
        return gtfsGraphLoader.getAdjacencyList();
    }

    // The schedule is a map of stop ID → list of scheduled connections, where each connection contains the departure time, route ID, and destination stop ID.
    @Override
    public Map<String, List<ScheduledConnection>> getSchedule() {
        return gtfsGraphLoader.getSchedule();
    }

    // The route short names map is a simple map of GTFS route ID → human-readable short name (e.g. "405", "DART"), used to label journey legs in the UI.
    @Override
    public Map<String, String> getRouteShortNames() {
        return gtfsGraphLoader.getRouteShortNames();
    }

    @Override
    public Map<String, List<double[]>> getRouteShapes() {
        return gtfsGraphLoader.getRouteShapes();
    }

    @Override
    public Map<String, List<FootpathEdge>> getFootpaths() {
        return gtfsGraphLoader.getFootpaths();
    }
}
