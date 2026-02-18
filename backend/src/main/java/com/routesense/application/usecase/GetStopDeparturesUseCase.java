package com.routesense.application.usecase;

import com.routesense.application.port.MapDataSource;
import com.routesense.domain.model.Departure;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Use case responsible for retrieving departures for a specific stop.
 *
 * This keeps controller logic thin and makes the behaviour testable.
 */

@Component
public class GetStopDeparturesUseCase {

    private final MapDataSource mapDataSource;

    public GetStopDeparturesUseCase(MapDataSource mapDataSource) {
        this.mapDataSource = mapDataSource;
    }

    public List<Departure> execute(Long stopId, boolean live) {
        return mapDataSource.getDeparturesForStop(stopId, live);
    }
}
