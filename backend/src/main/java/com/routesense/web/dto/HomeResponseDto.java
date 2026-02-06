package com.routesense.web.dto;

import java.util.List;

public class HomeResponseDto {
    private String location;
    private KpisDto kpis;
    private List<ModeUsageDto> modeUsage;
    private SummaryDto summary;

    public HomeResponseDto(String location, KpisDto kpis, List<ModeUsageDto> modeUsage, SummaryDto summary) {
        this.location = location;
        this.kpis = kpis;
        this.modeUsage = modeUsage;
        this.summary = summary;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public KpisDto getKpis() {
        return kpis;
    }

    public void setKpis(KpisDto kpis) {
        this.kpis = kpis;
    }

    public List<ModeUsageDto> getModeUsage() {
        return modeUsage;
    }

    public void setModeUsage(List<ModeUsageDto> modeUsage) {
        this.modeUsage = modeUsage;
    }

    public SummaryDto getSummary() {
        return summary;
    }

    public void setSummary(SummaryDto summary) {
        this.summary = summary;
    }
}
