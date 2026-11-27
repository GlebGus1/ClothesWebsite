package com.example.appShop.repositories.projection;

/**
 * Связка «товар -> заказ, в котором он ушёл». Нужна таблице «Товары (админ)»:
 * админ должен одним кликом перейти из позиции товара в заказ и обратно.
 */
public interface TshirtOrderRow {

    Long getTshirtId();

    Long getOrderId();
}
