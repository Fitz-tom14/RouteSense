package com.routesense.domain.model;

// Represents a single leg of a journey, e.g. "Bus 405 from westside to Galway (Ceannt), departing at 14:30 and arriving at 15:45".
public class JourneyLeg {

    private final String serviceName;   // e.g. "Bus 401", "Train IE" — used on the card
    private final String fromStopName; // e.g. "Ballinasloe Station"
    private final String toStopName; // e.g. "Galway (Ceannt)"
    private final String departureTime; // formatted "HH:mm"
    private final String arrivalTime;   // formatted "HH:mm"
    private final String mode;          // "Bus", "Train", "Walk", etc.

    // Constructor and getters
    public JourneyLeg(
            String serviceName, // e.g. "Bus 401", "Train IE" — used on the card
            String fromStopName,// e.g. "Ballinasloe Station"
            String toStopName,// e.g. "Galway (Ceannt)"
            String departureTime,// formatted "HH:mm"
            String arrivalTime,// formatted "HH:mm"
            String mode// "Bus", "Train", "Walk", etc.
    ) {
        this.serviceName   = serviceName;
        this.fromStopName  = fromStopName;
        this.toStopName    = toStopName;
        this.departureTime = departureTime;
        this.arrivalTime   = arrivalTime;
        this.mode          = mode;
    }

    public String getServiceName()   { return serviceName; }
    public String getFromStopName()  { return fromStopName; }
    public String getToStopName()    { return toStopName; }
    public String getDepartureTime() { return departureTime; }
    public String getArrivalTime()   { return arrivalTime; }
    public String getMode()          { return mode; }
}
