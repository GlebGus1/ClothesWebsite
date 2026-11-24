package com.example.appShop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки подтверждения e-mail (префикс "app.verification").
 *
 *  - enabled=false отключает проверку: регистрация сразу активирует аккаунт.
 *    Полезно в разработке, где нет почтового сервера (тогда PIN всё равно
 *    печатается в лог, но входить можно и без него).
 *  - codeTtl — сколько живёт выданный PIN-код (по умолчанию сутки).
 */
@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(
        Boolean enabled,
        Duration codeTtl
) {
    public boolean isEnabledOrDefault() {
        return enabled == null || enabled;
    }

    public Duration codeTtlOrDefault() {
        return codeTtl == null ? Duration.ofHours(24) : codeTtl;
    }
}
