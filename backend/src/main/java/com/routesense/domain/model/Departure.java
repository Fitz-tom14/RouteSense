package com.routesense.domain.model;

// Domain model representing a single upcoming departure from a stop.


public class Departure {
    private final String routeName;
    private final int minutesUntilArrival;

    public Departure(String routeName, int minutesUntilArrival) {
        this.routeName = routeName;
        this.minutesUntilArrival = minutesUntilArrival;
    }

    public String getRouteName() { return routeName; }
    public int getMinutesUntilArrival() { return minutesUntilArrival; }
}
