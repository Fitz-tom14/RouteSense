package com.routesense.web.dto;

/**
 * Request payload for journey search.
 */
public class SearchJourneyRequestDto {
    private String originStopId;
    private String destinationStopId;

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
}
