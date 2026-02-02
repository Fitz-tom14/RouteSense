package com.routesense.dto;

public class SummaryDto {
    private String title;
    private String subtitle;
    private String chartPlaceholder;

    public SummaryDto(String title, String subtitle, String chartPlaceholder) {
        this.title = title;
        this.subtitle = subtitle;
        this.chartPlaceholder = chartPlaceholder;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getChartPlaceholder() {
        return chartPlaceholder;
    }

    public void setChartPlaceholder(String chartPlaceholder) {
        this.chartPlaceholder = chartPlaceholder;
    }
}
