package com.routesense.web.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables local dev communication between:
 * - Vite frontend (default http://localhost:5173)
 * - Spring backend (http://localhost:8080)
 */

//Marks this as a Spring configuration class that runs on startup
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }
    // override the addCorsMapping method
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        String[] origins = corsProperties.getAllowedOrigins().split(",");
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
