package com.routesense.domain.model;

// Represents a directed edge in the stop graph, connecting two stops with a specific travel time and transport mode.
// For example, an edge from "stop_123" to "stop_456" with a travel time of 300 seconds and transport mode "BUS" would represent a bus connection between those two stops that takes 5 minutes.
public class StopEdge {
    private final String fromStopId;// the stop ID of the origin stop (e.g. "stop_123"), used to link edges together in the Dijkstra algorithm
    private final String toStopId;// the stop ID of the destination stop (e.g. "stop_456"), used to link edges together in the Dijkstra algorithm
    private final int travelTimeSeconds;// the time it takes to travel from the origin stop to the destination stop, in seconds
    private final TransportMode transportMode;// the mode of transport for this edge (e.g. "BUS", "TRAIN", "WALK")
    private final String routeId; // which route this edge belongs to (e.g. the GTFS route_id for Bus 402)

    // Constructors with varying levels of detail, depending on what information is available when building the graph. 
    // The most basic constructor just needs fromStopId, toStopId, and travelTimeSeconds, while the more detailed constructors can also include transport mode and route ID for better labeling and scoring of journeys.
    public StopEdge(String fromStopId, String toStopId, int travelTimeSeconds) {
        this(fromStopId, toStopId, travelTimeSeconds, null, null);
    }

    // Constructor that includes transport mode, 
    // which is important for scoring and labeling journeys.
    public StopEdge(String fromStopId, String toStopId, int travelTimeSeconds, TransportMode transportMode) {
        this(fromStopId, toStopId, travelTimeSeconds, transportMode, null);
    }

    // Full constructor that includes transport mode and route ID, which allows for the most detailed journey options with accurate mode summaries and service names.
    public StopEdge(String fromStopId, String toStopId, int travelTimeSeconds, TransportMode transportMode, String routeId) {
        this.fromStopId = fromStopId;
        this.toStopId = toStopId;
        this.travelTimeSeconds = travelTimeSeconds;
        this.transportMode = transportMode;
        this.routeId = routeId;
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

    public String getRouteId() {
        return routeId;
    }
}
