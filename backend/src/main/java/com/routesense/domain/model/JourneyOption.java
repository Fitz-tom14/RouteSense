package com.routesense.domain.model;

import java.util.List;

/// Domain model for a single journey option returned by the journey search.
//   This can represent either a scheduled journey (with detailed leg info) or a fallback Dijkstra path (without leg details, just a sequence of stops).
public class JourneyOption {
    private final JourneyOptionType type;// "SCHEDULED" or "FALLBACK_DIJKSTRA"
    private final List<Stop>        stops;// the sequence of stops in this journey, including origin and destination
    private final int               totalDurationSeconds;// total duration of the journey in seconds, including transfer times
    private final int               transfers;// number of transfers (0 for direct journeys, 1 for one transfer, etc.)
    private final double            co2Grams;// total estimated CO2 emissions for the journey in grams
    private final double            score;// the computed score for this journey option, used for ranking and recommendation
    private final boolean           recommended;// whether this journey is recommended (true for the top-ranked option, false for others)
    private final String            recommendationReason;// a human-readable explanation of why this journey is recommended (e.g. "Fastest option", "Lowest CO2 emissions", etc.)
    private final String            modeSummary;// a short summary of the modes used in this journey, e.g. "Bus → Train → Walk"
    private final List<JourneyLeg>  legs; // empty for fallback Dijkstra paths

    // Constructor with legs (for scheduled journeys) and without legs (for fallback Dijkstra paths)
    public JourneyOption(
            JourneyOptionType type,
            List<Stop>        stops,
            int               totalDurationSeconds,
            int               transfers,
            double            co2Grams,
            double            score,
            boolean           recommended,
            String            recommendationReason,
            String            modeSummary
    ) {
        // For fallback Dijkstra paths, we won't have leg details, so we can set legs to an empty list.
        this(type, stops, totalDurationSeconds, transfers, co2Grams,score, recommended, recommendationReason, modeSummary, List.of());
    }

    // Full constructor with legs (used for scheduled journeys)
    public JourneyOption(
            JourneyOptionType type,
            List<Stop>        stops,
            int               totalDurationSeconds,
            int               transfers,
            double            co2Grams,
            double            score,
            boolean           recommended,
            String            recommendationReason,
            String            modeSummary,
            List<JourneyLeg>  legs
    ) {
        // Basic validation (e.g. total duration should be positive, score should be between 0 and 1, etc.) could be added here if desired.
        this.type                 = type;
        this.stops                = stops;
        this.totalDurationSeconds = totalDurationSeconds;
        this.transfers            = transfers;
        this.co2Grams             = co2Grams;
        this.score                = score;
        this.recommended          = recommended;
        this.recommendationReason = recommendationReason;
        this.modeSummary          = modeSummary;
        this.legs                 = legs != null ? legs : List.of();
    }

    public JourneyOptionType  getType()                 { return type; }
    public List<Stop>         getStops()                { return stops; }
    public int                getTotalDurationSeconds()  { return totalDurationSeconds; }
    public int                getTransfers()             { return transfers; }
    public double             getCo2Grams()              { return co2Grams; }
    public double             getScore()                 { return score; }
    public boolean            isRecommended()            { return recommended; }
    public String             getRecommendationReason()  { return recommendationReason; }
    public String             getModeSummary()           { return modeSummary; }
    public List<JourneyLeg>   getLegs()                  { return legs; }
}
