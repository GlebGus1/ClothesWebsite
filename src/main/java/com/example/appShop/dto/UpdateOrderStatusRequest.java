package com.example.appShop.dto;

import com.example.appShop.entities.Order;
import jakarta.validation.constraints.NotNull;

/** Админ меняет статус заказа одной строкой JSON: {"status":"DELIVERED"}. */
public record UpdateOrderStatusRequest(
        @NotNull(message = "Неизвестный статус заказа")
        Order.OrderStatus status
) {
}
