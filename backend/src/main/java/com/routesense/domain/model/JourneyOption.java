package com.routesense.domain.model;

import java.util.List;

/**
 * Domain response model for a candidate journey between two stops.
 * Contains the list of stops in the journey, total duration, number of transfers, a score for ranking, and whether it's recommended.
 */
public class JourneyOption {
    private final JourneyOptionType type;
    private final List<Stop> stops;
    private final int totalDurationSeconds;
    private final int transfers;
    private final double co2Grams;
    private final double score;
    private final boolean recommended;
    private final String recommendationReason;
    private final String modeSummary;

    public JourneyOption(
            JourneyOptionType type,
            List<Stop> stops,
            int totalDurationSeconds,
            int transfers,
            double co2Grams,
            double score,
            boolean recommended,
            String recommendationReason,
            String modeSummary
    ) {
        this.type = type;
        this.stops = stops;
        this.totalDurationSeconds = totalDurationSeconds;
        this.transfers = transfers;
        this.co2Grams = co2Grams;
        this.score = score;
        this.recommended = recommended;
        this.recommendationReason = recommendationReason;
        this.modeSummary = modeSummary;
    }

    public JourneyOptionType getType() {
        return type;
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

    public double getCo2Grams() {
        return co2Grams;
    }

    public double getScore() {
        return score;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public String getRecommendationReason() {
        return recommendationReason;
    }

    public String getModeSummary() {
        return modeSummary;
    }
}
