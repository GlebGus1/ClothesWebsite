package com.example.appShop.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Настройки JWT из application.properties (префикс "jwt").
 *
 * Record + constructor binding: Spring создаёт неизменяемый объект со значениями из конфига.
 * Значения можно переопределить без пересборки: secrets.properties или JWT_SECRET (env).
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank(message = "jwt.secret должен быть задан")
        @Size(min = 64, message = "jwt.secret должен быть не короче 64 символов (требование HS512)")
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String issuer
) {
    public Duration accessTokenTtlOrDefault() {
        return accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
    }

    public Duration refreshTokenTtlOrDefault() {
        return refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
    }

    public String issuerOrDefault() {
        return (issuer == null || issuer.isBlank()) ? "appshop" : issuer;
    }
}
