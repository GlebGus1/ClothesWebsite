package com.example.appShop.dto;

import java.time.LocalDateTime;

/**
 * Строка таблицы «Товары (админ)».
 *
 * Чем отличается от публичного TShirtDto:
 *  - soldInOrderId — в каком заказе товар ушёл (покупателю это показывать нельзя,
 *    а админу нужно: бейдж «Заказ #4» ведёт сразу к заказу, а в заказах — к товару);
 *  - viewsCount — счётчик просмотров для сортировки и аналитики.
 */
public record AdminTShirtDto(
        long id,
        String name,
        String size,
        Double price,
        boolean available,
        boolean disabled,
        LocalDateTime dateOfCreated,
        Integer previewImageId,
        long viewsCount,
        Long collectionId,
        String collectionName,
        /** id заказа, в котором товар ушёл (null — товар не продавался/вернут в продажу). */
        Long soldInOrderId
) {
}
