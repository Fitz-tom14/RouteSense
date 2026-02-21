package com.routesense.infrastructure.tfi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "routesense.tfi")
public class TfiGtfsRtProperties {
    private String apiKey;
    private String vehiclePositionsUrl;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getVehiclePositionsUrl() { return vehiclePositionsUrl; }
    public void setVehiclePositionsUrl(String vehiclePositionsUrl) { this.vehiclePositionsUrl = vehiclePositionsUrl; }
}