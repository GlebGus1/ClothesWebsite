package com.example.appShop.dto;

import java.time.Instant;
import java.util.List;

/**
 * Как использован промокод: сколько раз, на какие суммы и кому.
 *
 * «Упущенная выгода» (discountAmount) — это реальные деньги, которые магазин
 * не получил, потому что дал скидку. Считаем по «слепкам» заказов, поэтому цифра
 * не меняется, даже если админ потом удалит или переименует сам код.
 */
public record PromoCodeUsageDto(
        String code,
        int timesUsed,
        /** Сумма позиций заказов с этим кодом ДО скидки. */
        double ordersSubtotal,
        /** Итог по таким заказам (после скидки). */
        double ordersTotal,
        /** Скидка, то есть упущенная выгода. */
        double discountAmount,
        List<OrderRefDto> orders
) {

    public record OrderRefDto(
            long orderId,
            String customerEmail,
            String nameOfUser,
            Instant createdAt,
            /** Статус заказа (PROCESSING/DELIVERED/...): отменённые в отчёт не попадают, но вид статусов полезен. */
            String status,
            int discountPercent,
            double subtotal,
            double discountAmount,
            double total
    ) {
    }
}
