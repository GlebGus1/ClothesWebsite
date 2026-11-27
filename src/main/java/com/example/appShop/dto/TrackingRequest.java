package com.example.appShop.dto;

import jakarta.validation.constraints.Size;

/**
 * Запрос на запись события аналитики (воронка продаж).
 *
 * visitorId — случайный uuid, который фронтенд сгенерировал сам и держит в
 * localStorage. Это НЕ cookie-идентификатор сервера и НЕ e-mail: по нему нельзя
 * понять, кто человек, а повторные события за один день мы схлопываем (dedupe_key).
 *
 * tshirtId обязателен для product-view / cart-add и не нужен для site-visit.
 */
public record TrackingRequest(
        @Size(max = 64, message = "Слишком длинный идентификатор посетителя")
        String visitorId,

        Long tshirtId
) {
    /** Ключ посетителя: если клиент его не прислал — считаем событие анонимным по IP. */
    public String visitorOrDefault(String fallback) {
        if (visitorId == null || visitorId.isBlank()) {
            return fallback;
        }
        return visitorId.trim();
    }
}
