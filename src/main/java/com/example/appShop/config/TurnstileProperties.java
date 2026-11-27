package com.example.appShop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cloudflare Turnstile (бесплатная замена reCAPTCHA) — защита формы регистрации
 * от автоматических спам-регистраций.
 *
 * Ключи кладутся в секреты (см. secrets.properties.example), в git попадает только
 * пустое значение. Если TURNSTILE_SECRET_KEY пустой — капча ВЫКЛЮЧЕНА: это удобно
 * для локальной разработки и CI, где нет внешних сервисов. Публичный ключ
 * (siteKey) фронтенд получает с GET /api/auth/captcha-status, чтобы не дублировать
 * его во втором конфиге.
 */
@ConfigurationProperties(prefix = "captcha.turnstile")
public record TurnstileProperties(
        String siteKey,
        String secretKey,

        @DefaultValue("https://challenges.cloudflare.com/turnstile/v0/siteverify") String verifyUrl,

        /** Сколько ждать ответа Cloudflare, мс. Больше — пользователь будет смотреть на spinner. */
        @DefaultValue("5000") int timeoutMs,

        /** Ограничение «сколько регистраций можно сделать с одного IP за окно». */
        @DefaultValue("3") int registrationsPerIpPerDay,

        @DefaultValue("24") int registrationsWindowHours
) {

    /** Капча активна только когда есть секретный ключ (server-side). */
    public boolean isEnabled() {
        return secretKey != null && !secretKey.isBlank();
    }

    /** Публичный ключ обязателен, только если включён секретный. */
    public boolean isConfigured() {
        return isEnabled() && siteKey != null && !siteKey.isBlank();
    }
}
