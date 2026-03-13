package com.routesense.application.usecase;

import com.routesense.domain.model.TransportMode;
import com.routesense.web.dto.HomeResponseDto;
import com.routesense.web.dto.KpisDto;
import com.routesense.web.dto.ModeUsageDto;
import com.routesense.web.dto.SummaryDto;
import org.springframework.stereotype.Component;

import java.util.List;

// This use case is responsible for generating the data needed to populate the Home page of the application.
@Component
public class GetHomeSummaryUseCase {

   // In a real implementation, this method would fetch and compute data based on the user's location and historical travel patterns.
    public HomeResponseDto execute(String location) {

        // Fallback text used when no location has been selected yet
        String effectiveLocation = (location == null || location.isBlank())
            ? "Select location"
            : location;

        // Placeholder KPI values
        KpisDto kpis = new KpisDto("-- min", "-- kg CO2");

        // Temporary mode usage breakdown for UI scaffolding
        List<ModeUsageDto> modeUsage = List.of(
            new ModeUsageDto(TransportMode.CAR, "--%"),
            new ModeUsageDto(TransportMode.BUS, "--%"),
            new ModeUsageDto(TransportMode.BIKE, "--%")
        );

        // Summary section placeholder content
        SummaryDto summary = new SummaryDto(
            "Weekly Summary",
            "Data unavailable",
            "Chart placeholder"
        );

        // Aggregate all sections into a single response for the Home page
        return new HomeResponseDto(effectiveLocation, kpis, modeUsage, summary);
    }
}
