package com.routesense.application.usecase;

import com.routesense.application.port.VehiclePositionsDataSource;
import com.routesense.domain.model.VehiclePosition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetLiveVehiclesUseCase {

    private final VehiclePositionsDataSource dataSource;

    public GetLiveVehiclesUseCase(VehiclePositionsDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<VehiclePosition> execute() {
        return dataSource.getLiveVehiclePositions();
    }
}