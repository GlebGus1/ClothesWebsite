package com.example.appShop.dto;

/**
 * Статус капчи для фронтенда: нужен ли вообще виджет Cloudflare Turnstile.
 *
 * siteKey — ПУБЛИЧНЫЙ ключ Turnstile (его всё равно видит браузер в разметке
 * виджета), поэтому отдавать его без токена безопасно. Фронтенду не нужно
 * дублировать ключ в своём .env: источник правды один — настройки бэкенда.
 */
public record CaptchaStatusDto(
        boolean required,
        String siteKey,
        String message
) {
}
