package com.smartparking.parking.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** Title of the generated API docs ({@code /v3/api-docs}, Swagger UI at {@code /swagger-ui.html}). */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Parking service API", version = "v1", description = "Locations, spots, pricing rules and real-time availability"))
public class OpenApiConfig {
}
