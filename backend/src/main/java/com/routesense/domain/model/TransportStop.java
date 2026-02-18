package com.routesense.domain.model;

/**
 * Domain model representing a transport stop that can be shown on the map.
 * It contains the stop's ID, name, location (latitude and longitude), and the transport mode (e.g., bus, tram, metro).
 */

public class TransportStop {
    private final long id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final TransportMode mode;

    public TransportStop(long id, String name, double latitude, double longitude, TransportMode mode) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.mode = mode;
    }   

    public long getID() { return id; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public TransportMode getMode() { return mode; }
    
}
