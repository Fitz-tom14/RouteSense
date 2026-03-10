package com.routesense.web.dto;

import com.routesense.domain.model.TransportMode;

// DTO for a transport stop on the map, used in MapController's getStops endpoint. Contains the stop ID, name, location (latitude and longitude), and transport mode (e.g. BUS, TRAIN).

public class MapStopDto {
    private String id;
    private String name;
    private double latitude;
    private double longitude;
    private TransportMode mode;

    public MapStopDto() {
    }

    // Full constructor with all fields, used when converting from the domain TransportStop to this DTO in the controller.
    public MapStopDto(String id, String name, double latitude, double longitude, TransportMode mode) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.mode = mode;
    }

    // Getters and setters for all fields, needed for JSON serialization/deserialization and for use in the controller.
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public TransportMode getMode() {
        return mode;
    }

    public void setMode(TransportMode mode) {
        this.mode = mode;
    }
}
