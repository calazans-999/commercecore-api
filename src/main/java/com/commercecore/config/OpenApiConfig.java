package com.commercecore.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI commerceCoreOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CommerceCore API")
                .version("1.0.0")
                .description(
                    "Professional E-Commerce REST API built with " +
                    "Java 21, Spring Boot 4, PostgreSQL and Docker. " +
                    "Features product management, categories, " +
                    "validation, pagination and automated tests."
                ));
    }
}