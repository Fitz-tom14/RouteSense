package com.routesense.web.dto;

import java.util.List;

/**
 * API response DTO for journey search.
 */
public class JourneySearchResponseDto {
    private List<JourneyOptionDto> options;
    private double carBaselineCo2Grams;

    // No-args constructor needed for JSON deserialization.
    public JourneySearchResponseDto() {
    }

    // All-args constructor for easy creation of DTO instances.
    public JourneySearchResponseDto(List<JourneyOptionDto> options, double carBaselineCo2Grams) {
        this.options = options;
        this.carBaselineCo2Grams = carBaselineCo2Grams;
    }

    // Getters and setters for all fields, needed for JSON serialization/deserialization and for use in the controller.
    public List<JourneyOptionDto> getOptions() {
        return options;
    }

    // Setter for options, needed for JSON deserialization.
    public void setOptions(List<JourneyOptionDto> options) {
        this.options = options;
    }

    // Getter for carBaselineCo2Grams, needed for JSON serialization.
    public double getCarBaselineCo2Grams() {
        return carBaselineCo2Grams;
    }

    // Setter for carBaselineCo2Grams, needed for JSON deserialization.
    public void setCarBaselineCo2Grams(double carBaselineCo2Grams) {
        this.carBaselineCo2Grams = carBaselineCo2Grams;
    }
}
