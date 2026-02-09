package com.routesense.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity used for persisting journey searches
 * to the database.
 */
@Entity
@Table(name = "journey_search")
public class JourneySearchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Required by JPA
    public JourneySearchEntity() {
    }

    public JourneySearchEntity(String origin, String destination, LocalDateTime createdAt) {
        this.origin = origin;
        this.destination = destination;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
