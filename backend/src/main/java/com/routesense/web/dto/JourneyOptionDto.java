package com.routesense.web.dto;

import java.util.List;

/**
 * API response DTO for a journey option.
 */
public class JourneyOptionDto {
    private List<StopDto> stops;
    private int totalDurationSeconds;
    private int transfers;
    private boolean recommended;

    public JourneyOptionDto() {
    }

    public JourneyOptionDto(List<StopDto> stops, int totalDurationSeconds, int transfers, boolean recommended) {
        this.stops = stops;
        this.totalDurationSeconds = totalDurationSeconds;
        this.transfers = transfers;
        this.recommended = recommended;
    }

    public List<StopDto> getStops() {
        return stops;
    }

    public void setStops(List<StopDto> stops) {
        this.stops = stops;
    }

    public int getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public void setTotalDurationSeconds(int totalDurationSeconds) {
        this.totalDurationSeconds = totalDurationSeconds;
    }

    public int getTransfers() {
        return transfers;
    }

    public void setTransfers(int transfers) {
        this.transfers = transfers;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }
}
