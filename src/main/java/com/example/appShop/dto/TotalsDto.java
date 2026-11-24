package com.example.appShop.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Денежные итоги заказа: сумма, скидка, итог к оплате.
 *
 * Считаем на сервере ОДИН раз (из цен базы данных), чтобы фронтенд не мог
 * «нарисовать» себе другую сумму: localStorage с корзиной правит пользователь.
 *
 * Скидка округляется до копеек (2 знака) по правилам HALF_UP — так «итог» совпадает
 * с тем, что продавец увидит в платёжке.
 */
public record TotalsDto(
        double subtotal,
        int discountPercent,
        double discountAmount,
        double total
) {

    public static TotalsDto of(double subtotal, Integer discountPercent) {
        int percent = (discountPercent == null || discountPercent < 1) ? 0 : discountPercent;
        double discount = round(subtotal * percent / 100.0);
        double total = round(subtotal - discount);
        return new TotalsDto(round(subtotal), percent, discount, Math.max(total, 0.0));
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
