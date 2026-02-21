package com.routesense.web.dto;

public record VehicleDto(
        String vehicleId,
        Double latitude,
        Double longitude,
        String routeId,
        Long timestamp
) {}