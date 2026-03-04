package com.routesense.domain.model;

/**
 * Directed edge between two stops with travel time in seconds.
 * Represents a connection in the routing graph, indicating how long it takes to travel from one stop to another.
 * 
 * 
 */
public class StopEdge {
    private final String fromStopId;
    private final String toStopId;
    private final int travelTimeSeconds;
    private final TransportMode transportMode;

    public StopEdge(String fromStopId, String toStopId, int travelTimeSeconds) {
        this(fromStopId, toStopId, travelTimeSeconds, null);
    }

    public StopEdge(String fromStopId, String toStopId, int travelTimeSeconds, TransportMode transportMode) {
        this.fromStopId = fromStopId;
        this.toStopId = toStopId;
        this.travelTimeSeconds = travelTimeSeconds;
        this.transportMode = transportMode;
    }

    public String getFromStopId() {
        return fromStopId;
    }

    public String getToStopId() {
        return toStopId;
    }

    public int getTravelTimeSeconds() {
        return travelTimeSeconds;
    }

    public TransportMode getTransportMode() {
        return transportMode;
    }
}
