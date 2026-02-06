package com.routesense.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check endpoint.
 */
@RestController
@RequestMapping("/health")
public class HealthController {
    @GetMapping
    public String health() {
        return "OK";
    }
}
