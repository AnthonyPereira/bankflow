package com.bankflow.auth.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Décrit l'API dans Swagger UI, disponible hors du profil {@code prod-sim}. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI authOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("BankFlow Auth Service")
                                .description("Inscription, connexion et gestion des sessions.")
                                .version("v1"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
