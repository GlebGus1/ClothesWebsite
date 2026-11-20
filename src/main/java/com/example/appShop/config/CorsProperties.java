package com.example.appShop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Список источников (origin), которым разрешено обращаться к API из браузера.
 * Vite-сервер живёт на http://localhost:5173 — его и добавляем.
 * В проде достаточно дописать адрес фронтенда в application.properties.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public List<String> originsOrDefault() {
        return (allowedOrigins == null || allowedOrigins.isEmpty())
                ? List.of("http://localhost:5173")
                : allowedOrigins;
    }
}
