package com.routesense;

import com.routesense.infrastructure.tfi.TfiGtfsRtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


/**
 * Main entry point for the RouteSense Spring Boot application.
 */

@SpringBootApplication
@EnableConfigurationProperties(TfiGtfsRtProperties.class)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
