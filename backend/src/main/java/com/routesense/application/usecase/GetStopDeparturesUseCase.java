package com.routesense.application.usecase;

import com.routesense.application.port.MapDataSource;
import com.routesense.domain.model.Departure;
import org.springframework.stereotype.Component;

import java.util.List;

/// Use case for retrieving departures for a specific stop, used by MapController to get live departure info for map stops.

@Component
public class GetStopDeparturesUseCase {

    private final MapDataSource mapDataSource;

    public GetStopDeparturesUseCase(MapDataSource mapDataSource) {
        this.mapDataSource = mapDataSource;
    }

    public List<Departure> execute(String stopId, boolean live) {
        return mapDataSource.getDeparturesForStop(stopId, live);
    }
}
