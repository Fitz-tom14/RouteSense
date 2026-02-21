package com.routesense.web.controller;

import com.routesense.application.usecase.GetLiveVehiclesUseCase;
import com.routesense.domain.model.VehiclePosition;
import com.routesense.web.dto.VehicleDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehiclesController {

    private final GetLiveVehiclesUseCase useCase;

    public VehiclesController(GetLiveVehiclesUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<VehicleDto> getVehicles() {
        List<VehiclePosition> vehicles = useCase.execute();
        return vehicles.stream()
                .map(v -> new VehicleDto(
                        v.getVehicleId(),
                        v.getLatitude(),
                        v.getLongitude(),
                        v.getRouteId(),
                        v.getTimestamp()
                ))
                .toList();
    }
}