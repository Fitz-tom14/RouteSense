package com.routesense.domain.model;

import java.util.List;

// Domain model for the result of a journey search, containing the list of journey options, the car baseline CO2 for comparison, and the car route geometry for map display.
public class JourneySearchResult {
    private final List<JourneyOption>    options;
    private final double                 carBaselineCo2Grams;
    private final List<List<Double>>     carRouteGeometry; // [[lat,lon],...] from ORS, null if unavailable

    public JourneySearchResult(List<JourneyOption> options, double carBaselineCo2Grams) {
        this(options, carBaselineCo2Grams, null);
    }

    /// Full constructor including car route geometry.
    public JourneySearchResult(List<JourneyOption> options, double carBaselineCo2Grams, List<List<Double>> carRouteGeometry) {
        this.options             = options;
        this.carBaselineCo2Grams = carBaselineCo2Grams;
        this.carRouteGeometry    = carRouteGeometry;
    }

    public List<JourneyOption>  getOptions()              { return options; }
    public double               getCarBaselineCo2Grams()  { return carBaselineCo2Grams; }
    public List<List<Double>>   getCarRouteGeometry()     { return carRouteGeometry; }
}
