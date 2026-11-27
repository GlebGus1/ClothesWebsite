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
        /**
         * Промокод, применённый к заказу (null — без скидки). Храним строкой-«слепком»:
         * заказ не должен меняться, если админ удалит или переименует сам код.
         */
        String promoCode,
        int discountPercent,
        double discountAmount,
        /** Итог к оплате = сумма позиций без скидки минус скидка. */
        double total,
        List<Item> items
) {

    /**
     * Позиция заказа. previewImageId добавлен ради админки: в таблице заказов нужно
     * показать мини-карточку товара с превью и ссылкой на его редактирование.
     */
    public record Item(long id, String name, String size, Double price, Integer previewImageId) {
    }
}
