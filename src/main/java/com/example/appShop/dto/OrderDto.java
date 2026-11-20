package com.example.appShop.dto;

import com.example.appShop.entities.Order;

import java.time.Instant;
import java.util.List;

/**
 * Заказ наружу. Entity Order в JSON отдавать нельзя: там есть ленивые связи
 * (tshirts, user), которые при сериализации либо падают (LazyInitializationException),
 * либо тянут за собой лишние таблицы.
 */
public record OrderDto(
        long id,
        Order.OrderStatus status,
        String nameOfUser,
        String postOffice,
        String postalCode,
        String phoneNumber,
        Instant createdAt,
        Long customerId,
        String customerEmail,
        List<Item> items
) {

    /** Позиция заказа: только то, что нужно интерфейсу (без картинок и полей склада). */
    public record Item(long id, String name, String size, Double price) {
    }
}
