package com.example.appShop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Создаёт администратора при старте, если заданы свойства app.admin.*.
 * По умолчанию выключено (email пустой) — не хочется держать в проде пароль «admin/admin».
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminBootstrapProperties(
        String email,
        String password,
        String firstName,
        String lastName
) {
    public boolean isEnabled() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
