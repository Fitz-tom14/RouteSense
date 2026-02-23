package com.routesense.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

//makes it a Spring bean so it can be injected elsewhere
@Component
//tells Spring to bind values from application.properties that start with app.cors.
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
    private String allowedOrigins;

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
