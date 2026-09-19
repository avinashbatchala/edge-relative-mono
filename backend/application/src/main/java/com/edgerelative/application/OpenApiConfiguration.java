package com.edgerelative.application;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the operator/engineering UI.
 *
 * <p>The spec documents read-only broker capability plus the represented-but-disabled mutation
 * contracts. It never exposes credentials: only broker-neutral application DTOs are described.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI edgeRelativeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Edge Relative API")
                        .version("0.1.0-SNAPSHOT")
                        .description("""
                                Broker-neutral application API for Edge Relative.

                                Groww read-only capabilities are implemented. Broker-side mutations are
                                represented but disabled: they return 501 BROKER_OPERATION_NOT_ENABLED and
                                make no downstream request.
                                """));
    }
}
