package com.example.appShop.dto;

import java.time.Instant;

/** Промокод наружу (админка и подтверждение в заказе). Сущность PromoCode в JSON не отдаём. */
public record PromoCodeDto(
        long id,
        String code,
        int discountPercent,
        boolean active,
        Instant expiresAt,
        Instant createdAt
) {
}
