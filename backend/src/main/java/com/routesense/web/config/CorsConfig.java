package com.routesense.web.config;


import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Global CORS configuration to allow requests from the frontend React app running on localhost:3000 during development.
// This is needed to avoid CORS errors when the frontend tries to call the backend API.
// In production, this should be locked down to only allow requests from the actual frontend domain.

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
                .allowedOrigins(Arrays.stream(origins).toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
