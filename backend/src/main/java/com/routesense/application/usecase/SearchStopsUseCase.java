package com.routesense.application.usecase;

import com.routesense.domain.model.Stop;
import com.routesense.application.port.StopGraphRepository;
import com.routesense.web.dto.StopSearchResultDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SearchStopsUseCase {

    private final StopGraphRepository stopGraphRepository;

    public SearchStopsUseCase(StopGraphRepository stopGraphRepository) {
        this.stopGraphRepository = stopGraphRepository;
    }

    // Searches stops by name and returns a small list for autocomplete
    public List<StopSearchResultDto> execute(String query) {

        // basic guard so we don’t run a pointless search
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String q = query.toLowerCase(Locale.ROOT);
        Map<String, Stop> stops = stopGraphRepository.getStops();

        return stops.entrySet().stream()
                // match if stop name contains the query
                .filter(e -> e.getValue().getName().toLowerCase(Locale.ROOT).contains(q))
                .limit(10) // keep results small for UI
                .map(e -> new StopSearchResultDto(
                        e.getKey(),
                        e.getValue().getName(),
                        e.getValue().getLatitude(),
                        e.getValue().getLongitude()
                ))
                .toList();
    }
}