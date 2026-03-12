package com.routesense.web.dto;

import java.util.List;

// DTO for a single leg of a journey option, used in JourneyOptionDto. Contains info about the service, stops, times, and mode for that leg.
// This is a separate DTO from the domain JourneyLeg to keep the API response stable even if we change the internal domain model later.
public class JourneyLegDto {

    private String serviceName;   // e.g. "Bus 401", "Train IE"
    private String fromStopName;
    private String toStopName;
    private String departureTime; // "HH:mm"
    private String arrivalTime;   // "HH:mm"
    private String mode;          // "Bus", "Train", "Walk"
    private List<double[]> shapePoints; // GTFS shape geometry: ordered [lat, lon] pairs (null if unavailable)

    public JourneyLegDto() {}

    public JourneyLegDto(
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

    public String getServiceName()           { return serviceName; }
    public void   setServiceName(String v)   { this.serviceName = v; }

    public String getFromStopName()          { return fromStopName; }
    public void   setFromStopName(String v)  { this.fromStopName = v; }

    public String getToStopName()            { return toStopName; }
    public void   setToStopName(String v)    { this.toStopName = v; }

    public String getDepartureTime()         { return departureTime; }
    public void   setDepartureTime(String v) { this.departureTime = v; }

    public String getArrivalTime()           { return arrivalTime; }
    public void   setArrivalTime(String v)   { this.arrivalTime = v; }

    public String getMode()                  { return mode; }
    public void   setMode(String v)          { this.mode = v; }

    public List<double[]> getShapePoints()           { return shapePoints; }
    public void           setShapePoints(List<double[]> v) { this.shapePoints = v; }
}
