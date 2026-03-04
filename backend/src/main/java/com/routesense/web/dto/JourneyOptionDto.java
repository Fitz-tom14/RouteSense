package com.routesense.web.dto;

import java.util.List;

/**
 * API response DTO for a journey option.
 */
public class JourneyOptionDto {
    private String type; // e.g. "WALK", "BUS", "TRAIN", etc.
    private List<StopDto> stops;// List of stops in the journey option, with details for each stop.
    private int durationSeconds;// Total duration in seconds for the entire journey option.
    private int transfers;// Number of transfers in the journey option.
    private double co2Grams;// Estimated CO2 emissions in grams for the journey option.
    private double score;// Score for ranking the journey option, higher is better.
    private boolean recommended;// Whether this journey option is recommended to the user.
    private String recommendationReason;// Explanation for why this option is recommended (if recommended is true).
    private String modeSummary;// A summary of the modes of transport used in this journey option, e.g. "Walk + Bus + Train".

    // No-args constructor for JSON deserialization.
    public JourneyOptionDto() {
    }

    // All-args constructor for easy creation of DTO instances.
    public JourneyOptionDto(
            String type,
            List<StopDto> stops,
            int durationSeconds,
            int transfers,
            double co2Grams,
            double score,
            boolean recommended,
            String recommendationReason,
            String modeSummary
    ) {
        // Map the constructor parameters to the class fields.
        this.type = type;
        this.stops = stops;
        this.durationSeconds = durationSeconds;
        this.transfers = transfers;
        this.co2Grams = co2Grams;
        this.score = score;
        this.recommended = recommended;
        this.recommendationReason = recommendationReason;
        this.modeSummary = modeSummary;
    }

    // Getters and setters for all fields, needed for JSON serialization/deserialization and for use in the controller.
    public String getType() {
        return type;
    }

    // Setter for type, needed for JSON deserialization.
    public void setType(String type) {
        this.type = type;
    }

    // Getters and setters for the rest of the fields.
    public List<StopDto> getStops() {
        return stops;
    }

    // Setter for stops, needed for JSON deserialization.
    public void setStops(List<StopDto> stops) {
        this.stops = stops;
    }

    // Getters and setters for the rest of the fields.
    public int getDurationSeconds() {
        return durationSeconds;
    }

    // Setter for durationSeconds, needed for JSON deserialization.
    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    // Getters and setters for the rest of the fields.
    public int getTransfers() {
        return transfers;
    }

    // Setter for transfers, needed for JSON deserialization.
    public void setTransfers(int transfers) {
        this.transfers = transfers;
    }

    //
    public double getCo2Grams() {
        return co2Grams;
    }

    // Setter for co2Grams, needed for JSON deserialization.
    public void setCo2Grams(double co2Grams) {
        this.co2Grams = co2Grams;
    }

    // Getters and setters for the rest of the fields.
    public double getScore() {
        return score;
    }

    // Setter for score, needed for JSON deserialization.
    public void setScore(double score) {
        this.score = score;
    }

    // Getters and setters for the rest of the fields.
    public boolean isRecommended() {
        return recommended;
    }

    // Setter for recommended, needed for JSON deserialization.
    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }

    // Getter for recommendationReason, needed for JSON serialization.
    public String getRecommendationReason() {
        return recommendationReason;
    }

    // Setter for recommendationReason, needed for JSON deserialization.
    public void setRecommendationReason(String recommendationReason) {
        this.recommendationReason = recommendationReason;
    }

    // Getter for modeSummary, needed for JSON serialization.
    public String getModeSummary() {
        return modeSummary;
    }

    // Setter for modeSummary, needed for JSON deserialization.
    public void setModeSummary(String modeSummary) {
        this.modeSummary = modeSummary;
    }
}
