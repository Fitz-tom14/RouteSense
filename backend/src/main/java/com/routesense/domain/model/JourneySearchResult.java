package com.routesense.domain.model;

import java.util.List;

/**
 * Domain model for journey search output.
 */
public class JourneySearchResult {
    private final List<JourneyOption> options;
    private final double carBaselineCo2Grams;

    public JourneySearchResult(List<JourneyOption> options, double carBaselineCo2Grams) {
        this.options = options;
        this.carBaselineCo2Grams = carBaselineCo2Grams;
    }

    public List<JourneyOption> getOptions() {
        return options;
    }

    public double getCarBaselineCo2Grams() {
        return carBaselineCo2Grams;
    }
}
