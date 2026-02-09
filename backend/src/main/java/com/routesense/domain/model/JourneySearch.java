package com.routesense.domain.model;

import java.time.LocalDateTime;

/**
 * Domain model representing a journey search.
 * Kept free of framework-specific annotations.
 */
public class JourneySearch {
    private Long id;
    private String origin;
    private String destination;
    private LocalDateTime createdAt;

    public JourneySearch(Long id, String origin, String destination, LocalDateTime createdAt) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
