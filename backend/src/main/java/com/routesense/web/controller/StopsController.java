package com.routesense.web.controller;

import com.routesense.application.usecase.SearchStopsUseCase;
import com.routesense.web.dto.StopSearchResultDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Handles stop search requests from the frontend.
@RestController
@RequestMapping("/api/stops")
@CrossOrigin(origins = "http://localhost:5173") // allow React dev server to call this
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