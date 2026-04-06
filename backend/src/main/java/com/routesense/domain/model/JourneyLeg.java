package com.routesense.domain.model;

import java.util.List;

// Represents a single leg of a journey A to B,
public class JourneyLeg {

    private final String serviceName;   // e.g. "Bus 401", "Train IE" — used on the card
    private final String fromStopName; // e.g. "Ballinasloe Station"
    private final String toStopName; // e.g. "Galway (Ceannt)"
    private final String departureTime; // formatted "HH:mm"
    private final String arrivalTime;   // formatted "HH:mm"
    private final String mode;          // "Bus", "Train", "Walk", etc.
    private final List<double[]> shapePoints; // ordered [lat, lon] pairs from GTFS shapes.txt (null if unavailable)

    // Constructor and getters
    public JourneyLeg(
            String serviceName,
            String fromStopName,
            String toStopName,
            String departureTime,
            String arrivalTime,
            String mode,
            List<double[]> shapePoints
    ) {
        this.serviceName   = serviceName;
        this.fromStopName  = fromStopName;
        this.toStopName    = toStopName;
        this.departureTime = departureTime;
        this.arrivalTime   = arrivalTime;
        this.mode          = mode;
        this.shapePoints   = shapePoints;
    }

    public String getServiceName()         { return serviceName; }
    public String getFromStopName()        { return fromStopName; }
    public String getToStopName()          { return toStopName; }
    public String getDepartureTime()       { return departureTime; }
    public String getArrivalTime()         { return arrivalTime; }
    public String getMode()                { return mode; }
    public List<double[]> getShapePoints() { return shapePoints; }
}
