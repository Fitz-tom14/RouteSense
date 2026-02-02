package com.routesense.controller;

import com.routesense.dto.HomeResponseDto;
import com.routesense.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/home")
    public HomeResponseDto getHome(@RequestParam(required = false) String location) {
        return homeService.getHome(location);
    }
}
