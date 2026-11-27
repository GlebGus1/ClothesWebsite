package com.example.appShop.repositories.projection;

import com.example.appShop.entities.Order;

import java.time.Instant;

/**
 * Одна строка «заказ × позиция» для аналитики (интерфейсная проекция Spring Data).
 *
 * Почему проекция, а не List<Order>: для дашборда нам не нужны сами сущности
 * (с их ленивыми связями и картинками longblob) — нужны только id, статус,
 * промокод, процент скидки и цена позиции. Один запрос вместо «заказ + его товары».
 */
public interface OrderLineRow {

    Long getOrderId();

    Order.OrderStatus getStatus();

    String getPromoCode();

    Integer getDiscountPercent();

    Double getPrice();

    String getCustomerEmail();

    String getNameOfUser();

    Instant getCreatedAt();
}
