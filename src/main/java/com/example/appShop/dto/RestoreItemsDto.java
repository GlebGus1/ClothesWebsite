package com.example.appShop.dto;

import java.util.List;

/**
 * Результат ручного «вернуть товары в продажу» из отменённого заказа.
 * order — обновлённый заказ, чтобы фронтенд перерисовал строку без перезагрузки.
 */
public record RestoreItemsDto(
        OrderDto order,
        int restoredCount,
        List<String> skippedNames
) {
}
