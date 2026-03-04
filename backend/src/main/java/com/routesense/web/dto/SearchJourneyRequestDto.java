package com.routesense.web.dto;

/**
 * Request payload for journey search.
 * Origin can be provided as a stop ID or as geographic coordinates (lat/lon).
 */
public class SearchJourneyRequestDto {
    private String originStopId;
    private String destinationStopId;
    private Double originLat;
    private Double originLon;

    public SearchJourneyRequestDto() {
    }

    public SearchJourneyRequestDto(String originStopId, String destinationStopId) {
        this.originStopId = originStopId;
        this.destinationStopId = destinationStopId;
    }

    public String getOriginStopId() {
        return originStopId;
    }

    public void setOriginStopId(String originStopId) {
        this.originStopId = originStopId;
    }

    public String getDestinationStopId() {
        return destinationStopId;
    }

    public void setDestinationStopId(String destinationStopId) {
        this.destinationStopId = destinationStopId;
    }

    public Double getOriginLat() {
        return originLat;
    }

    public void setOriginLat(Double originLat) {
        this.originLat = originLat;
    }

    public Double getOriginLon() {
        return originLon;
    }

    public void setOriginLon(Double originLon) {
        this.originLon = originLon;
    }
}
