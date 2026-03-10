package com.routesense.web.dto;

import java.util.List;

// DTO for a single journey option in the journey search response, used in JourneySearchResponseDto.
//Contains info about the type of journey (e.g. "public_transport", "car_fallback"), the list of stops, total duration, number of transfers, CO2 emissions, score, recommendation status and reason, mode summary, and the list of legs for transit journeys.
public class JourneyOptionDto {
    private String type;
    private List<StopDto> stops;
    private int durationSeconds;
    private int transfers;
    private double co2Grams;
    private double score;
    private boolean recommended;
    private String recommendationReason;
    private String modeSummary;
    private List<JourneyLegDto> legs; // per-leg service name and times (empty for fallback paths)

    // No-args constructor for JSON deserialization.
    public JourneyOptionDto() {
    }

    public JourneyOptionDto(
            String type,
            List<StopDto> stops,
            int durationSeconds,
            int transfers,
            double co2Grams,
            double score,
            boolean recommended,
            String recommendationReason,
            String modeSummary,
            List<JourneyLegDto> legs
    ) {
        this.type                 = type;
        this.stops                = stops;
        this.durationSeconds      = durationSeconds;
        this.transfers            = transfers;
        this.co2Grams             = co2Grams;
        this.score                = score;
        this.recommended          = recommended;
        this.recommendationReason = recommendationReason;
        this.modeSummary          = modeSummary;
        this.legs                 = legs;
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

    public void setModeSummary(String modeSummary) {
        this.modeSummary = modeSummary;
    }

    public List<JourneyLegDto> getLegs() {
        return legs;
    }

    public void setLegs(List<JourneyLegDto> legs) {
        this.legs = legs;
    }
}
