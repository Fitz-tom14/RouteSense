package com.routesense.dto;

import com.routesense.domain.TransportMode;

public class ModeUsageDto {
    private TransportMode mode;
    private String percentText;

    public ModeUsageDto(TransportMode mode, String percentText) {
        this.mode = mode;
        this.percentText = percentText;
    }

    public TransportMode getMode() {
        return mode;
    }

    public void setMode(TransportMode mode) {
        this.mode = mode;
    }

    public String getPercentText() {
        return percentText;
    }

    public void setPercentText(String percentText) {
        this.percentText = percentText;
    }
}
