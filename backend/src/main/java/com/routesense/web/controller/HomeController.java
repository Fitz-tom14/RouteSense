package com.routesense.web.controller;

import com.routesense.application.usecase.GetHomeSummaryUseCase;
import com.routesense.web.dto.HomeResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller: calls use case, returns DTO.
 */
@RestController
@RequestMapping("/api")
public class HomeController {
    private final GetHomeSummaryUseCase getHomeSummaryUseCase;

    public HomeController(GetHomeSummaryUseCase getHomeSummaryUseCase) {
        this.getHomeSummaryUseCase = getHomeSummaryUseCase;
    }

    @GetMapping("/home")
    public HomeResponseDto getHome(@RequestParam(required = false) String location) {
        return getHomeSummaryUseCase.execute(location);
    }
}
