package com.example.appShop.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 *
 * В интерфейсе есть кнопка Authorize — вставляем туда access-токен и пробуем ручки руками.
 * SecurityScheme описывает, что токен передаётся как "Authorization: Bearer <token>".
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI appShopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clothes Shop API")
                        .version("1.0")
                        .description("REST API магазина футболок: JWT (access + refresh), каталог, заказы"))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .name(SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
