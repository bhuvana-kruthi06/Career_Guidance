package edu.careerguidance.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI careerGuidanceApi() {
        return new OpenAPI().info(new Info()
                .title("Pathfinder Career Guidance API")
                .version("1.0.0")
                .description("Career matching, discovery, skill resources, and optional account services. Match scores are profile similarity measures, not hiring probabilities.")
                .contact(new Contact().name("Pathfinder project")));
    }
}
