package com.example.appShop.dto;

import java.time.Instant;

/**
 * Ответ на проверку промокода: сам код, процент и ПЕРЕСЧИТАННЫЕ сервером деньги.
 * Именно эти числа фронтенд показывает в корзине — они совпадают с тем, что
 * бэкенд посчитает при оформлении заказа.
 */
public record PromoValidationDto(
        String code,
        int discountPercent,
        Instant expiresAt,
        TotalsDto totals,
        String message
) {
}
