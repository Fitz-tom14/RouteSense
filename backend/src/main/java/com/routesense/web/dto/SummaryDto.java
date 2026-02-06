package com.routesense.web.dto;

public class SummaryDto {
    private String title;
    private String content;
    private String chartData;

    public SummaryDto(String title, String content, String chartData) {
        this.title = title;
        this.content = content;
        this.chartData = chartData;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChartData() {
        return chartData;
    }

    public void setChartData(String chartData) {
        this.chartData = chartData;
    }
}
