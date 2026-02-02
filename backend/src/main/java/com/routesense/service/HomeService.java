package com.routesense.service;

import com.routesense.domain.TransportMode;
import com.routesense.dto.HomeResponseDto;
import com.routesense.dto.KpisDto;
import com.routesense.dto.ModeUsageDto;
import com.routesense.dto.SummaryDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HomeService {

    public HomeResponseDto getHome(String location) {
        String effectiveLocation = (location == null || location.isBlank()) 
            ? "Select location" 
            : location;

        KpisDto kpis = new KpisDto("-- min", "-- kg CO2");

        List<ModeUsageDto> modeUsage = List.of(
            new ModeUsageDto(TransportMode.CAR, "--%"),
            new ModeUsageDto(TransportMode.BUS, "--%"),
            new ModeUsageDto(TransportMode.BIKE, "--%")
        );

        SummaryDto summary = new SummaryDto(
            "Weekly Summary",
            "Data unavailable",
            "Chart placeholder"
        );

        return new HomeResponseDto(effectiveLocation, kpis, modeUsage, summary);
    }
}
