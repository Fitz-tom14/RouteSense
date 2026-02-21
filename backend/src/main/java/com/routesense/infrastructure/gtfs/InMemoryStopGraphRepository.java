package com.routesense.infrastructure.gtfs;

import com.routesense.application.port.StopGraphRepository;
import com.routesense.domain.model.Stop;
import com.routesense.domain.model.StopEdge;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * In-memory repository over the GTFS-loaded stop graph.
 */
@Component
public class InMemoryStopGraphRepository implements StopGraphRepository {

    private final GtfsGraphLoader gtfsGraphLoader;

    public InMemoryStopGraphRepository(GtfsGraphLoader gtfsGraphLoader) {
        this.gtfsGraphLoader = gtfsGraphLoader;
    }

    @Override
    public Map<String, Stop> getStops() {
        return gtfsGraphLoader.getStops();
    }

    @Override
    public Map<String, List<StopEdge>> getAdjacencyList() {
        return gtfsGraphLoader.getAdjacencyList();
    }
}
