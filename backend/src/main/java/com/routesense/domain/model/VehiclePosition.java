package com.routesense.domain.model;

public class VehiclePosition {
    private final String vehicleId;
    private final Double latitude;
    private final Double longitude;
    private final String routeId;
    private final Long timestamp;

    public VehiclePosition(String vehicleId, Double latitude, Double longitude, String routeId, Long timestamp) {
        this.vehicleId = vehicleId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.routeId = routeId;
        this.timestamp = timestamp;
    }

    public String getVehicleId() { return vehicleId; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getRouteId() { return routeId; }
    public Long getTimestamp() { return timestamp; }
}