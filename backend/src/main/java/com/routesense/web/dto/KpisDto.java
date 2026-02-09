package com.routesense.web.dto;

/**
 * DTO representing KPI values shown on the Home page.
 */

public class KpisDto {
    private String avgJourneyTimeText;
    private String co2PerJourneyText;

    public KpisDto(String avgJourneyTimeText, String co2PerJourneyText) {
        this.avgJourneyTimeText = avgJourneyTimeText;
        this.co2PerJourneyText = co2PerJourneyText;
    }

    public String getAvgJourneyTimeText() {
        return avgJourneyTimeText;
    }

    public void setAvgJourneyTimeText(String avgJourneyTimeText) {
        this.avgJourneyTimeText = avgJourneyTimeText;
    }

    public String getCo2PerJourneyText() {
        return co2PerJourneyText;
    }

    public void setCo2PerJourneyText(String co2PerJourneyText) {
        this.co2PerJourneyText = co2PerJourneyText;
    }
}
