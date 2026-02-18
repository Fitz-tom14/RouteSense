package com.routesense.web.dto;

/**
 * Web DTO for a single departure row in the popup.
 */

public class DepartureDto {
    private String routeName;
    private int minutes;

    public DepartureDto() {
    }

    public DepartureDto(String routeName, int minutes) {
        this.routeName = routeName;
        this.minutes = minutes;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }
}
