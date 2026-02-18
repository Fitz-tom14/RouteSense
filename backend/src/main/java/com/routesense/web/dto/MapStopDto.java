package com.routesense.web.dto;

import com.routesense.domain.model.TransportMode;

/**
 * Web DTO for a map stop marker.
 * Keeps the API response stable even if domain changes internally.
 */

public class MapStopDto {
    private Long id;
    private String name;
    private double latitude;
    private double longitude;
    private TransportMode mode;

    public MapStopDto() {
    }

    public MapStopDto(Long id, String name, double latitude, double longitude, TransportMode mode) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.mode = mode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
