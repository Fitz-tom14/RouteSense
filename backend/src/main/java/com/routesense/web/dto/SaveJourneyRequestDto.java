package com.routesense.web.dto;

// This is a Data Transfer Object (DTO) class that represents the request body for saving a new journey record.
// It contains fields that correspond to the properties of a JourneyRecord, along with a userId
public record SaveJourneyRequestDto(
        long timestamp,
        String date,
        int durationSeconds,
        double co2Grams,
        double carCo2Grams,
        String modeSummary,
        String destination,
        int transfers,
        String userId) {}
