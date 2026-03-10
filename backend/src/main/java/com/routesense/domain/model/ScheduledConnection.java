package com.routesense.domain.model;

// Represents a scheduled connection from one stop to another, 
// used in the schedule-aware Dijkstra algorithm.
public class ScheduledConnection {

    private final String routeId;
    private final String routeShortName; // e.g. "411", "409", "DART" - used for mode summary display
    private final String toStopId;
    private final int departureTimeSeconds; // seconds since midnight - when bus leaves the 'from' stop
    private final int arrivalTimeSeconds;   // seconds since midnight - when bus arrives at 'to' stop
    private final TransportMode mode;

    // Constructor and getters
    public ScheduledConnection(String routeId, String routeShortName, String toStopId, int departureTimeSeconds, int arrivalTimeSeconds, TransportMode mode) {
        this.routeId = routeId; // e.g. "route_1234" - used to look up the human-readable short name and other route details
        this.routeShortName = routeShortName;// e.g. "411", "409", "DART" - used for mode summary display
        this.toStopId = toStopId; // the stop ID of the next stop this connection goes to (e.g. "stop_5678"), used to link connections together in the Dijkstra algorithm
        this.departureTimeSeconds = departureTimeSeconds;
        this.arrivalTimeSeconds = arrivalTimeSeconds;
        this.mode = mode;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getRouteShortName() {
        return routeShortName;
    }

    public String getToStopId() {
        return toStopId;
    }

    public int getDepartureTimeSeconds() {
        return departureTimeSeconds;
    }

    public int getArrivalTimeSeconds() {
        return arrivalTimeSeconds;
    }

    public TransportMode getMode() {
        return mode;
    }
}
