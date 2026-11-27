package com.example.appShop.dto;

import java.time.Instant;

/** Коллекция (дроп) наружу: и для публичного баннера/чипсов, и для админки. */
public record CollectionDto(
        long id,
        String name,
        String description,
        /** Цвет акцента в формате #RRGGBB (null — фронтенд берёт цвет по умолчанию). */
        String badgeColor,
        boolean active,
        Instant createdAt,
        /** Сколько товаров сейчас в коллекции — админ сразу видит «пустую» коллекцию. */
        long productsCount
) {
}
