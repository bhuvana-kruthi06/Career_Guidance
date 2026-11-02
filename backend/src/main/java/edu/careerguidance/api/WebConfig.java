package edu.careerguidance.api;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final List<String> allowedOrigins;

    public WebConfig(@Value("${career-guidance.cors-origins:}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) return;
        registry.addMapping("/api/**").allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Request-ID", "Authorization", "X-XSRF-TOKEN")
                .exposedHeaders("X-Request-ID", "Retry-After")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
