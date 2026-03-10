package com.routesense.web.dto;

/**
 * Request payload for journey search.
 * Origin and destination can each be provided as a stop ID or as geographic coordinates (lat/lon).
 */
public class SearchJourneyRequestDto {
    private String  originStopId;
    private String  destinationStopId;
    private Double  originLat;
    private Double  originLon;
    private Double  destinationLat;
    private Double  destinationLon;
    private Integer departureTimeSeconds;
    private Integer arriveBySeconds;

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

    public Double getDestinationLat() {
        return destinationLat;
    }

    public void setDestinationLat(Double destinationLat) {
        this.destinationLat = destinationLat;
    }

    public Double getDestinationLon() {
        return destinationLon;
    }

    public void setDestinationLon(Double destinationLon) {
        this.destinationLon = destinationLon;
    }

    public Integer getDepartureTimeSeconds() {
        return departureTimeSeconds;
    }

    public void setDepartureTimeSeconds(Integer departureTimeSeconds) {
        this.departureTimeSeconds = departureTimeSeconds;
    }

    public Integer getArriveBySeconds() {
        return arriveBySeconds;
    }

    public void setArriveBySeconds(Integer arriveBySeconds) {
        this.arriveBySeconds = arriveBySeconds;
    }
}
