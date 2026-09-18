package com.itilms.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Swagger UI for each service, pre-wired with the bearer scheme so a reviewer
 * can paste a token once and exercise the whole API (Doc S23, criterion 8).
 *
 * <p>The gateway aggregates these documents, so every service must describe
 * itself accurately — the aggregated page is only as good as its parts.
 */
@Configuration
public class CommonOpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI openApi(@Value("${spring.application.name:IT-ILMS Service}") String applicationName,
                    @Value("${itilms.docs.description:}") String description,
                    @Value("${itilms.docs.gateway-url:http://localhost:8080}") String gatewayUrl) {

        return new OpenAPI()
                .info(new Info()
                        .title(displayName(applicationName))
                        .version("1.0.0")
                        .description(description.isBlank()
                                ? "IT Institute Learning Management System — " + applicationName
                                : description)
                        .contact(new Contact().name("IT-ILMS Engineering"))
                        .license(new License().name("Proprietary")))
                .addServersItem(new Server().url(gatewayUrl).description("API Gateway"))
                .addServersItem(new Server().url("/").description("This service, directly"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token issued by POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /** "identity-service" reads better as "Identity Service" on the docs page. */
    private String displayName(String applicationName) {
        String[] words = applicationName.split("[-_]");
        StringBuilder builder = new StringBuilder("IT-ILMS ");
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(' ');
        }
        return builder.toString().trim() + " API";
    }
}
