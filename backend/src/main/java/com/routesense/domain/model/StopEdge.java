package com.routesense.domain.model;

/**
 * Directed edge between two stops with travel time in seconds.
 */
public class StopEdge {
    private final String fromStopId;
    private final String toStopId;
    private final int travelTimeSeconds;

    public StopEdge(String fromStopId, String toStopId, int travelTimeSeconds) {
        this.fromStopId = fromStopId;
        this.toStopId = toStopId;
        this.travelTimeSeconds = travelTimeSeconds;
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
}
