package com.routesense.application.usecase;

import com.routesense.application.port.MapDataSource;
import com.routesense.domain.model.TransportMode;
import com.routesense.domain.model.TransportStop;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Use case responsible for retrieving map stops for the Map View screen.
 *
 * This keeps controller logic thin and makes the behaviour testable.
 */

@Component
public class GetMapStopsUseCase {

    private final MapDataSource mapDataSource;
    
    public GetMapStopsUseCase(MapDataSource mapDataSource) {
        this.mapDataSource = mapDataSource;
    }

    public List<TransportStop> execute(String location, Set<TransportMode> modes, boolean live) {
        String effectiveLocation = (location == null || location.isBlank())
                ? "Default"
                : location;


        return mapDataSource.getAllStops(effectiveLocation, modes, live);
    }
}
