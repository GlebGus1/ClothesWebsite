package com.example.appShop.services;

import com.example.appShop.dto.AnalyticsDashboardDto;
import com.example.appShop.dto.AnalyticsDashboardDto.FunnelDto;
import com.example.appShop.dto.AnalyticsDashboardDto.StepDto;
import com.example.appShop.dto.AnalyticsDashboardDto.TopProductDto;
import com.example.appShop.dto.PromoCodeUsageDto;
import com.example.appShop.entities.Order;
import com.example.appShop.entities.TShirt;
import com.example.appShop.entities.TrackingEvent;
import com.example.appShop.repositories.OrderRepository;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.TrackingEventRepository;
import com.example.appShop.repositories.UserRepository;
import com.example.appShop.repositories.projection.FavoriteCountRow;
import com.example.appShop.repositories.projection.OrderLineRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Аналитика магазина: выручка, скидки, воронка продаж, топы товаров и статистика
 * промокодов. Всё считается на сервере, чтобы админская страница «Статистика»
 * не тянула всю историю заказов в браузер.
 *
 * Откуда цифры (важно для понимания):
 *  - заказы и их суммы — из таблицы orders (это источник правды для денег);
 *  - шаги воронки «визиты/просмотры/корзины» — из обезличенных tracking_events,
 *    которые пишет фронтенд с согласия пользователя (см. TrackingService).
 *
 * Выручка = сумма позиций НЕОТМЕНЁННЫХ заказов за вычетом скидки промокода.
 * Отменённые заказы в выручку не входят, но показаны отдельно (cancelledOrdersCount),
 * чтобы админ видел, сколько заказов «сгорело».
 */
@Service
public class AnalyticsService {

    /**
     * Сколько позиций показывать в «топе». Настраивается свойством app.analytics.top-size
     * (см. application.properties). Поле с @Value, а не параметр конструктора — намеренно:
     * в юнит-тестах сервис собирается через new и должен видеть предсказуемое значение
     * по умолчанию, без поднятия всего Spring-контекста.
     */
    @Value("${app.analytics.top-size:5}")
    private int topSize = 5;

    private final OrderRepository orderRepository;
    private final TShirtRepository tShirtRepository;
    private final UserRepository userRepository;
    private final TrackingEventRepository trackingEventRepository;

    public AnalyticsService(OrderRepository orderRepository,
                           TShirtRepository tShirtRepository,
                           UserRepository userRepository,
                           TrackingEventRepository trackingEventRepository) {
        this.orderRepository = orderRepository;
        this.tShirtRepository = tShirtRepository;
        this.userRepository = userRepository;
        this.trackingEventRepository = trackingEventRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDashboardDto dashboard() {
        List<OrderAggregate> orders = loadOrders();

        double subtotal = 0.0;
        double revenue = 0.0;
        double discounts = 0.0;
        long cancelled = 0;
        long liveOrders = 0;

        for (OrderAggregate order : orders) {
            if (order.cancelled()) {
                cancelled++;
                continue;
            }
            liveOrders++;
            subtotal += order.subtotal;
            revenue += order.total();
            discounts += order.discountAmount();
        }

        FunnelDto funnel = buildFunnel(liveOrders);

        return new AnalyticsDashboardDto(
                round(revenue),
                round(discounts),
                round(subtotal),
                liveOrders,
                cancelled,
                tShirtRepository.countByIsDisabledFalse(),
                userRepository.count(),
                funnel,
                topViewed(),
                topFavorited());
    }

    /**
     * Статистика по промокодам: код -> сколько раз применён, суммы заказов,
     * размер скидки и сами заказы (кто и когда).
     *
     * Считаем по «слепкам» в заказах (orders.promo_code + discount_percent), а не по
     * таблице promo_codes: код могли удалить или переименовать, а история скидок
     * обязана остаться честной.
     *
     * Отменённые заказы в отчёт НЕ входят — так же, как они не входят в выручку:
     * скидка по коду, который «сгорел» вместе с заказом, магазину денег не стоила.
     */
    @Transactional(readOnly = true)
    public List<PromoCodeUsageDto> promoCodeUsage() {
        Map<String, List<OrderAggregate>> byCode = new LinkedHashMap<>();
        for (OrderAggregate order : loadOrders()) {
            if (order.cancelled() || order.promoCode == null || order.promoCode.isBlank()) {
                continue;
            }
            byCode.computeIfAbsent(order.promoCode.toUpperCase(), key -> new ArrayList<>()).add(order);
        }

        List<PromoCodeUsageDto> report = new ArrayList<>();
        for (Map.Entry<String, List<OrderAggregate>> entry : byCode.entrySet()) {
            double codesSubtotal = 0.0;
            double codesTotal = 0.0;
            double discount = 0.0;
            List<PromoCodeUsageDto.OrderRefDto> refs = new ArrayList<>();

            for (OrderAggregate order : entry.getValue()) {
                codesSubtotal += order.subtotal;
                codesTotal += order.total();
                discount += order.discountAmount();
                refs.add(new PromoCodeUsageDto.OrderRefDto(
                        order.id, order.customerEmail, order.nameOfUser, order.createdAt,
                        order.status.name(),
                        order.discountPercent, round(order.subtotal), round(order.discountAmount()),
                        round(order.total())));
            }

            report.add(new PromoCodeUsageDto(entry.getKey(), entry.getValue().size(),
                    round(codesSubtotal), round(codesTotal), round(discount), refs));
        }

        // самые «дорогие» по упущенной выгоде — наверху: так админ сразу видит,
        // какие акции реально стоят магазину денег
        return report.stream()
                .sorted(Comparator.comparingDouble(PromoCodeUsageDto::discountAmount).reversed())
                .toList();
    }

    /** Один запрос «заказ × позиция» и склейка строк в заказы в памяти. */
    private List<OrderAggregate> loadOrders() {
        Map<Long, OrderAggregate> orders = new LinkedHashMap<>();
        for (OrderLineRow row : orderRepository.allOrderLines()) {
            OrderAggregate order = orders.computeIfAbsent(row.getOrderId(), id -> newOrder(row, id));
            order.addPrice(row.getPrice());
        }
        return new ArrayList<>(orders.values());
    }

    private OrderAggregate newOrder(OrderLineRow row, Long id) {
        OrderAggregate order = new OrderAggregate();
        order.id = id;
        order.status = row.getStatus();
        order.promoCode = row.getPromoCode();
        order.discountPercent = row.getDiscountPercent() == null ? 0 : row.getDiscountPercent();
        order.customerEmail = row.getCustomerEmail();
        order.nameOfUser = row.getNameOfUser();
        order.createdAt = row.getCreatedAt();
        return order;
    }

    /**
     * Воронка: визиты -> просмотры карточек -> добавления в корзину -> заказы.
     *
     * Как читать график: шаги 1–3 считаются только по тем посетителям, которые
     * согласились на статистику (см. cookie-баннер), а заказы — это реальные цифры
     * из таблицы orders. Если часть покупателей отказалась от cookies, нижний шаг
     * иногда оказывается больше верхнего; прогресс-бар во фронтенде ограничен 100%,
     * поэтому график не «вылезет» за контейнер.
     */
    private FunnelDto buildFunnel(long liveOrders) {
        long visits = trackingEventRepository.countByEventType(TrackingEvent.EventType.SITE_VISIT);
        long views = trackingEventRepository.countByEventType(TrackingEvent.EventType.PRODUCT_VIEW);
        long cartAdds = trackingEventRepository.countByEventType(TrackingEvent.EventType.CART_ADD);

        List<StepDto> steps = List.of(
                new StepDto("visits", "Визиты на сайт", visits, 100.0, 100.0),
                new StepDto("productViews", "Просмотры товаров", views, percent(views, visits), percent(views, visits)),
                new StepDto("cartAdds", "Добавления в корзину", cartAdds, percent(cartAdds, views), percent(cartAdds, visits)),
                new StepDto("orders", "Оформленные заказы", liveOrders,
                        percent(liveOrders, cartAdds), percent(liveOrders, visits)));

        return new FunnelDto(visits, views, cartAdds, liveOrders, steps);
    }

    private List<TopProductDto> topViewed() {
        return tShirtRepository.findMostViewed(PageRequest.of(0, topSize)).stream()
                .map(tShirt -> toTop(tShirt, tShirt.getViewsCount()))
                .toList();
    }

    /**
     * Топ по «сердечкам». Порядок задаёт запрос (group by + order by count desc),
     * а карточки подтягиваем одним findAllById и раскладываем обратно в том же порядке.
     */
    private List<TopProductDto> topFavorited() {
        List<FavoriteCountRow> ranking = userRepository.findFavoriteCounts(PageRequest.of(0, topSize));
        if (ranking.isEmpty()) {
            return List.of();
        }
        List<Long> ids = ranking.stream().map(FavoriteCountRow::getTshirtId).toList();
        Map<Long, TShirt> products = new LinkedHashMap<>();
        tShirtRepository.findAllById(ids).forEach(tShirt -> products.put(tShirt.getId(), tShirt));

        List<TopProductDto> top = new ArrayList<>();
        for (FavoriteCountRow row : ranking) {
            TShirt tShirt = products.get(row.getTshirtId());
            if (tShirt != null) {
                top.add(toTop(tShirt, row.getFavoritesCount() == null ? 0L : row.getFavoritesCount()));
            }
        }
        return top;
    }

    private TopProductDto toTop(TShirt tShirt, long metric) {
        Integer preview = tShirt.getPreviewImageId() > 0 ? tShirt.getPreviewImageId() : null;
        return new TopProductDto(tShirt.getId(), tShirt.getName(), tShirt.getSize(), tShirt.getPrice(),
                preview, tShirt.isAvailable(), metric);
    }

    private double percent(long part, long whole) {
        if (whole <= 0) {
            return 0.0;
        }
        return round(part * 100.0 / whole);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Внутренний «счётчик» одного заказа: сумма позиций + скидка + статус. */
    private static final class OrderAggregate {

        private long id;
        private Order.OrderStatus status;
        private String promoCode;
        private int discountPercent;
        private String customerEmail;
        private String nameOfUser;
        private Instant createdAt;
        private double subtotal;

        private void addPrice(Double price) {
            subtotal += price == null ? 0.0 : price;
        }

        private boolean cancelled() {
            return status == Order.OrderStatus.CANCELED;
        }

        private double discountAmount() {
            return subtotal * discountPercent / 100.0;
        }

        private double total() {
            return subtotal - discountAmount();
        }
    }
}
