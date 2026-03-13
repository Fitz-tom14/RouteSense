package com.routesense.application.usecase;

import com.routesense.application.port.MapDataSource;
import com.routesense.domain.model.TransportMode;
import com.routesense.domain.model.TransportStop;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

// This use case is responsible for fetching the transport stops to be displayed on the Map page, based on the user's selected location and transport mode filters.
// It interacts with the MapDataSource to retrieve the relevant data, which is currently mocked for development purposes.
@Component
public class GetMapStopsUseCase {

    // The MapDataSource is injected into the use case, allowing it to fetch the necessary data for the Map page.
    //  In a real implementation, this would likely involve fetching data from an external API or database.
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
