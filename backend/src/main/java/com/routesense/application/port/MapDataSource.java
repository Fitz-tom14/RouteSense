package com.routesense.application.port;

import com.routesense.domain.model.Departure;
import com.routesense.domain.model.TransportMode;
import com.routesense.domain.model.TransportStop;

import java.util.List;
import java.util.Set;

/**
 * Application port that abstracts where map data comes from.
 */

public interface MapDataSource {
    //getAllStop Retrieves all transport stops in a given location, filtered by transport modes and live status.
    List<TransportStop> getAllStops(String location, Set<TransportMode> modes, boolean live);

    List<Departure> getDeparturesForStop(long stopID, boolean live);
    
}
