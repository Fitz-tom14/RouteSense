package com.routesense.web.controller;

import com.routesense.application.usecase.SearchStopsUseCase;
import com.routesense.web.dto.StopSearchResultDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Handles stop search requests from the frontend.
@RestController
@RequestMapping("/api/stops")
public class StopsController {

    //inject the use case to keep the controller thin and focused on HTTP handling.
    private final SearchStopsUseCase searchStopsUseCase;

    public StopsController(SearchStopsUseCase searchStopsUseCase) {
        this.searchStopsUseCase = searchStopsUseCase;
    }

    // GET /api/stops/search?query=eyre
    @GetMapping("/search")
    public List<StopSearchResultDto> search(@RequestParam("query") String query) {
        return searchStopsUseCase.execute(query);
    }
}