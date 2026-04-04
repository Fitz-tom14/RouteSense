package com.routesense.web.dto;

import java.util.List;

// DTO carrying one journey option back to the frontend — score, emissions, stops, legs, etc.
// Jackson needs the no-arg constructor + getters/setters to serialise this as JSON.
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
    private List<JourneyLegDto> legs; // empty for fallback Dijkstra paths — no per-leg detail there

    public JourneyOptionDto() {}

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

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<StopDto> getStops() { return stops; }
    public void setStops(List<StopDto> stops) { this.stops = stops; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public int getTransfers() { return transfers; }
    public void setTransfers(int transfers) { this.transfers = transfers; }

    public double getCo2Grams() { return co2Grams; }
    public void setCo2Grams(double co2Grams) { this.co2Grams = co2Grams; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public boolean isRecommended() { return recommended; }
    public void setRecommended(boolean recommended) { this.recommended = recommended; }

    public String getRecommendationReason() { return recommendationReason; }
    public void setRecommendationReason(String recommendationReason) { this.recommendationReason = recommendationReason; }

    public String getModeSummary() { return modeSummary; }
    public void setModeSummary(String modeSummary) { this.modeSummary = modeSummary; }

    public List<JourneyLegDto> getLegs() { return legs; }
    public void setLegs(List<JourneyLegDto> legs) { this.legs = legs; }
}
