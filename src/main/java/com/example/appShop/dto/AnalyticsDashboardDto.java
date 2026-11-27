package com.example.appShop.dto;

import java.util.List;

/**
 * Дашборд «Статистика и аналитика» в админке.
 *
 * Все считаются на сервере одним-двумя запросами, а не «обходом всех заказов
 * во фронтенде»: клиент не должен тянуть ради дашборда всю историю продаж.
 */
public record AnalyticsDashboardDto(
        /** Выручка = суммы всех неотменённых заказов с учётом скидок промокодов. */
        double revenue,
        /** Сумма предоставленных скидок (упущенная выгода) по НЕотменённым заказам. */
        double discountsAmount,
        /** Сумма позиций всех неотменённых заказов ДО скидок. */
        double subtotal,
        long ordersCount,
        long cancelledOrdersCount,
        long productsCount,
        long customersCount,
        FunnelDto funnel,
        List<TopProductDto> mostViewed,
        List<TopProductDto> mostFavorited
) {

    /**
     * Воронка продаж: Визиты -> Просмотры товаров -> Добавления в корзину -> Заказы.
     * Конверсии считаем дважды: от предыдущего шага (где «потерялись» люди) и от
     * первого шага (общая конверсия сайта).
     */
    public record FunnelDto(
            long visits,
            long productViews,
            long cartAdds,
            long orders,
            List<StepDto> steps
    ) {
    }

    public record StepDto(
            String key,
            String label,
            long value,
            /** Процент от предыдущего шага (0..100). */
            double conversionFromPrevious,
            /** Процент от визитов (0..100). */
            double conversionFromVisits
    ) {
    }

    /** Товар в топе: metric — просмотры или добавления в избранное (зависит от таблицы). */
    public record TopProductDto(
            long id,
            String name,
            String size,
            Double price,
            Integer previewImageId,
            boolean available,
            long metric
    ) {
    }
}
