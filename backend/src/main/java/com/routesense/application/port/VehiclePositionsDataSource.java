package com.routesense.application.port;

import com.routesense.domain.model.VehiclePosition;
import java.util.List;

public interface VehiclePositionsDataSource {
    List<VehiclePosition> getLiveVehiclePositions();
}