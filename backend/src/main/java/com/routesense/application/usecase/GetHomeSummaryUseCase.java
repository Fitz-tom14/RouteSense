package com.routesense.application.usecase;

import com.routesense.domain.model.TransportMode;
import com.routesense.web.dto.HomeResponseDto;
import com.routesense.web.dto.KpisDto;
import com.routesense.web.dto.ModeUsageDto;
import com.routesense.web.dto.SummaryDto;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Use case: Generate home page summary.
 * Replaces HomeService with explicit use case naming.
 */
@Component
public class GetHomeSummaryUseCase {

    public HomeResponseDto execute(String location) {
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
