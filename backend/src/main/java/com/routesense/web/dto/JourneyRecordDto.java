package com.routesense.web.dto;

// This is a Data Transfer Object (DTO) class that represents a journey record in the web layer.
// It is used to transfer data between the backend and the frontend, and it contains fields that
public record JourneyRecordDto(
        Long id,
        long timestamp,
        String date,
        int durationSeconds,
        double co2Grams,
        double carCo2Grams,
        String modeSummary,
        String destination,
        int transfers) {}
