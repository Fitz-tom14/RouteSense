package com.routesense.web.dto;

import java.util.List;

// DTO for the response of a journey search API call, used in JourneyController.
// Contains the list of journey options, the car baseline CO2 for comparison, and the car route geometry for map display.
public class JourneySearchResponseDto {
    private List<JourneyOptionDto> options;
    private double                 carBaselineCo2Grams;
    // Road-following polyline for the car baseline: [[lat,lon],...]. Null if ORS unavailable. 
    private List<List<Double>>     carRouteGeometry;

    public JourneySearchResponseDto() {}

    public JourneySearchResponseDto(List<JourneyOptionDto> options, double carBaselineCo2Grams,
                                    List<List<Double>> carRouteGeometry) {
        this.options             = options;
        this.carBaselineCo2Grams = carBaselineCo2Grams;
        this.carRouteGeometry    = carRouteGeometry;
    }

    public List<JourneyOptionDto> getOptions()                                   { return options; }
    public void setOptions(List<JourneyOptionDto> options)                       { this.options = options; }

    public double getCarBaselineCo2Grams()                                       { return carBaselineCo2Grams; }
    public void setCarBaselineCo2Grams(double v)                                 { this.carBaselineCo2Grams = v; }

    public List<List<Double>> getCarRouteGeometry()                              { return carRouteGeometry; }
    public void setCarRouteGeometry(List<List<Double>> carRouteGeometry)         { this.carRouteGeometry = carRouteGeometry; }
}
