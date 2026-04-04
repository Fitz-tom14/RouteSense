package com.routesense.domain.model;

import java.util.List;

// One journey option from the search — either a scheduled GTFS journey (with leg detail) or a fallback Dijkstra path (stops only).
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

    // Fallback Dijkstra paths don't have leg detail, so they use this constructor and get an empty legs list.
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
        this(type, stops, totalDurationSeconds, transfers, co2Grams,score, recommended, recommendationReason, modeSummary, List.of());
    }

    // Scheduled journeys use this one — legs carry the per-segment service name, times, and shape points.
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
        this.type                 = type;
        this.stops                = stops;
        this.totalDurationSeconds = totalDurationSeconds;
        this.transfers            = transfers;
        this.co2Grams             = co2Grams;
        this.score                = score;
        this.recommended          = recommended;
        this.recommendationReason = recommendationReason;
        this.modeSummary          = modeSummary;
        this.legs                 = legs != null ? legs : List.of(); // guard — always an empty list rather than null
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
