package com.routesense.application.usecase;

import com.routesense.domain.model.TransportMode;
import com.routesense.web.dto.HomeResponseDto;
import com.routesense.web.dto.KpisDto;
import com.routesense.web.dto.ModeUsageDto;
import com.routesense.web.dto.SummaryDto;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Use case responsible for assembling the data required
 * to render the Home page summary.
 *
 * At this stage, the data is placeholder-only and will be
 * replaced with real calculations and data sources later.
 */
@Component
public class GetHomeSummaryUseCase {

    /**
     * Builds the home page response based on the selected location.
     */
    public HomeResponseDto execute(String location) {

        // Fallback text used when no location has been selected yet
        String effectiveLocation = (location == null || location.isBlank())
            ? "Select location"
            : location;

        // Placeholder KPI values (to be replaced with real metrics)
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
