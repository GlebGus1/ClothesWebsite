package com.example.appShop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки исходящей почты (префикс "app.mail").
 *
 * Сам SMTP (host/port/логин) описан стандартными ключами spring.mail.* — их читает
 * Spring Boot и на их основании создаёт бин JavaMailSender. Здесь только «наше»:
 * адрес отправителя. Ключи можно переопределить без пересборки: secrets.properties
 * или переменные окружения (MAIL_FROM).
 */
@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(
        String from
) {
    public String fromOrDefault() {
        return (from == null || from.isBlank()) ? "noreply@clothes.shop" : from.trim();
    }
}
