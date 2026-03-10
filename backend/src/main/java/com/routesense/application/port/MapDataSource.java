package com.routesense.application.port;

import com.routesense.domain.model.Departure;
import com.routesense.domain.model.TransportMode;
import com.routesense.domain.model.TransportStop;

import java.util.List;
import java.util.Set;

/// Port interface for map-related data retrieval, used by use cases to abstract away data source details.

public interface MapDataSource {
    //getAllStop Retrieves all transport stops in a given location, filtered by transport modes and live status.
    List<TransportStop> getAllStops(String location, Set<TransportMode> modes, boolean live);

    List<Departure> getDeparturesForStop(String stopId, boolean live);
    
}
