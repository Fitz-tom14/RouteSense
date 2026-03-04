package com.routesense.application.service;

import com.routesense.domain.model.TransportMode;
import org.springframework.stereotype.Component;

/**
 * Centralized emissions and distance calculations for route comparison.
 * Emission factors are approximate and intended for comparison only.
 */
@Component
public class EmissionsCalculator {

    // Emission factors in grams of CO2 per kilometer for different transport modes.
    public static final double WALK_G_PER_KM = 0.0;// Walking is considered carbon neutral for this application.
    public static final double BIKE_G_PER_KM = 8.0;// Approximate emissions for bike manufacturing and maintenance per km.
    public static final double TRAM_LUAS_G_PER_KM = 35.0;// Approximate emissions for tram/light rail per km.
    public static final double TRAIN_G_PER_KM = 45.0;// Approximate emissions for train travel per km.
    public static final double BUS_G_PER_KM = 105.0;// Approximate emissions for bus travel per km.
    public static final double DEFAULT_PUBLIC_TRANSPORT_G_PER_KM = BUS_G_PER_KM;// Fallback for public transport modes not specifically categorized.
    public static final double CAR_G_PER_KM = 170.0;// Approximate emissions for an average car per km.

    private static final double EARTH_RADIUS_KM = 6371.0;// Average radius of the Earth in kilometers.

    // Estimates CO2 emissions for a given distance and transport mode.
    public double estimateEdgeCo2Grams(double distanceKm, TransportMode mode) {
        return distanceKm * emissionFactor(mode);
    }

    // Estimates CO2 emissions for a car given the road distance.
    public double estimateCarCo2Grams(double roadDistanceKm) {
        return roadDistanceKm * CAR_G_PER_KM;
    }

    // Returns the emission factor for a given transport mode.
    public double emissionFactor(TransportMode mode) {
        if (mode == null) {
            // Fallback when edge mode is not available from graph data.
            return DEFAULT_PUBLIC_TRANSPORT_G_PER_KM;
        }

        // Return specific emission factors for known modes, otherwise use a default for public transport.
        return switch (mode) {
            case WALK -> WALK_G_PER_KM;
            case BIKE -> BIKE_G_PER_KM;
            case TRAM, LUAS -> TRAM_LUAS_G_PER_KM;
            case TRAIN -> TRAIN_G_PER_KM;
            case BUS -> BUS_G_PER_KM;
            default -> DEFAULT_PUBLIC_TRANSPORT_G_PER_KM;
        };
    }

    // Haversine formula to calculate the great-circle distance between two points on the Earth.
    public double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        // Haversine formula
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        // Calculate the great-circle distance in kilometers.
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
