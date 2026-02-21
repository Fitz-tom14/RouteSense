package com.routesense.domain.model;

import java.util.List;

/**
 * Domain response model for a candidate journey between two stops.
 */
public class JourneyOption {
    private final List<Stop> stops;
    private final int totalDurationSeconds;
    private final int transfers;
    private final double score;
    private final boolean recommended;

    public JourneyOption(List<Stop> stops, int totalDurationSeconds, int transfers, double score, boolean recommended) {
        this.stops = stops;
        this.totalDurationSeconds = totalDurationSeconds;
        this.transfers = transfers;
        this.score = score;
        this.recommended = recommended;
    }

    public List<Stop> getStops() {
        return stops;
    }

    public int getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public int getTransfers() {
        return transfers;
    }

    public double getScore() {
        return score;
    }

    public boolean isRecommended() {
        return recommended;
    }
}
