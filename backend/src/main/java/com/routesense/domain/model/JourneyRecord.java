package com.routesense.domain.model;

import jakarta.persistence.*;

// JPA entity — @Entity tells Spring this class maps to a database table, @Table sets the table name.
@Entity
@Table(name = "journey_records")
public class JourneyRecord {

    @Id                                                    // primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // auto-increment, DB assigns the value
    private Long id;

    private long timestamp;        // Unix epoch in milliseconds — when the journey was saved
    private String date;           // human-readable date string for display in the history list
    private int durationSeconds;
    private double co2Grams;       // CO2 for the public transport route the user took
    private double carCo2Grams;    // CO2 if they'd driven the same route — stored so the history page can show the saving
    private String modeSummary;
    private String destination;
    private int transfers;
    private String userId;

    protected JourneyRecord() {} // JPA requires a no-arg constructor — protected so nothing else accidentally uses it

    public JourneyRecord(long timestamp, String date, int durationSeconds, double co2Grams, double carCo2Grams, String modeSummary, String destination, int transfers, String userId) {
        this.timestamp = timestamp;
        this.date = date;
        this.durationSeconds = durationSeconds;
        this.co2Grams = co2Grams;
        this.carCo2Grams = carCo2Grams;
        this.modeSummary = modeSummary;
        this.destination = destination;
        this.transfers = transfers;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public String getDate() { return date; }
    public int getDurationSeconds() { return durationSeconds; }
    public double getCo2Grams() { return co2Grams; }
    public double getCarCo2Grams() { return carCo2Grams; }
    public String getModeSummary() { return modeSummary; }
    public String getDestination() { return destination; }
    public int getTransfers() { return transfers; }
    public String getUserId() { return userId; }
}
