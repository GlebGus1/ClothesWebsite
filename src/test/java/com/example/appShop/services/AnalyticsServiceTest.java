package com.example.appShop.services;

import com.example.appShop.dto.AnalyticsDashboardDto;
import com.example.appShop.dto.AnalyticsDashboardDto.StepDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Аналитика: деньги (выручка/скидки) и воронка продаж.
 *
 * Главный смысл тестов — не «сломалась ли формула сложения», а правила, которые
 * админ обещает клиентам: отменённый заказ не приносит выручку и не считается
 * потраченной скидкой, промо-отчёт считается по «слепкам» заказов, а воронка
 * не падает на пустом магазине (делений на ноль).
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TShirtRepository tShirtRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TrackingEventRepository trackingEventRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    /**
     * Заглука интерфейсной проекции Spring Data (одна строка «заказ × позиция»).
     * Методы именно getXxx(): интерфейс проекции требует их, а не аксессоров рекорда.
     */
    private record Line(Long orderId, Order.OrderStatus status, String promoCode, Integer discountPercent,
                        Double price, String customerEmail, String nameOfUser, Instant createdAt)
            implements OrderLineRow {

        @Override
        public Long getOrderId() {
            return orderId;
        }

        @Override
        public Order.OrderStatus getStatus() {
            return status;
        }

        @Override
        public String getPromoCode() {
            return promoCode;
        }

        @Override
        public Integer getDiscountPercent() {
            return discountPercent;
        }

        @Override
        public Double getPrice() {
            return price;
        }

        @Override
        public String getCustomerEmail() {
            return customerEmail;
        }

        @Override
        public String getNameOfUser() {
            return nameOfUser;
        }

        @Override
        public Instant getCreatedAt() {
            return createdAt;
        }

        private static Line of(long orderId, Order.OrderStatus status, String code, int percent,
                               double price, String email) {
            return new Line(orderId, status, code, percent, price, email, "Иван Петров",
                    Instant.parse("2024-05-01T10:00:00Z"));
        }
    }

    /** Заглука проекции «сколько раз товар полюбили». */
    private record Fav(Long tshirtId, Long favoritesCount) implements FavoriteCountRow {

        @Override
        public Long getTshirtId() {
            return tshirtId;
        }

        @Override
        public Long getFavoritesCount() {
            return favoritesCount;
        }
    }

    @BeforeEach
    void stubBackgroundCounters() {
        // эти счётчики нужны любому дашборду, но не являются предметом проверки каждого теста
        lenient().when(tShirtRepository.countByIsDisabledFalse()).thenReturn(42L);
        lenient().when(userRepository.count()).thenReturn(7L);
        lenient().when(tShirtRepository.findMostViewed(any(Pageable.class))).thenReturn(List.of());
        lenient().when(userRepository.findFavoriteCounts(any(Pageable.class))).thenReturn(List.of());
    }

    private static TShirt shirt(long id, String name, long views) {
        TShirt tShirt = new TShirt();
        tShirt.setId(id);
        tShirt.setName(name);
        tShirt.setSize("M");
        tShirt.setPrice(199.0);
        tShirt.setAvailable(true);
        tShirt.setViewsCount(views);
        return tShirt;
    }

    @Test
    void revenueIgnoresCancelledOrdersAndSubtractsPromoDiscount() {
        when(orderRepository.allOrderLines()).thenReturn(List.of(
                // живой заказ 1: позиции 100 + 100, промокод SALE10 => скидка 20, к оплате 180
                Line.of(1L, Order.OrderStatus.DELIVERED, "SALE10", 10, 100.0, "a@example.com"),
                Line.of(1L, Order.OrderStatus.DELIVERED, "SALE10", 10, 100.0, "a@example.com"),
                // отменённый заказ на 500 не должен попадать ни в выручку, ни в скидки
                Line.of(2L, Order.OrderStatus.CANCELED, "SALE10", 10, 500.0, "b@example.com")));

        AnalyticsDashboardDto dashboard = analyticsService.dashboard();

        assertThat(dashboard.revenue()).isEqualTo(180.0);
        assertThat(dashboard.discountsAmount()).isEqualTo(20.0);
        assertThat(dashboard.subtotal()).isEqualTo(200.0);
        assertThat(dashboard.ordersCount()).isEqualTo(1);
        assertThat(dashboard.cancelledOrdersCount()).isEqualTo(1);
    }

    @Test
    void orderWithoutPromoCodeGivesNoDiscount() {
        when(orderRepository.allOrderLines())
                .thenReturn(List.of(Line.of(3L, Order.OrderStatus.PROCESSING, null, 0, 300.0, "c@example.com")));

        AnalyticsDashboardDto dashboard = analyticsService.dashboard();

        assertThat(dashboard.revenue()).isEqualTo(300.0);
        assertThat(dashboard.discountsAmount()).isZero();
    }

    @Test
    void emptyShopDashboardIsZerosAndNotANullPointer() {
        when(orderRepository.allOrderLines()).thenReturn(List.of());

        AnalyticsDashboardDto dashboard = analyticsService.dashboard();

        assertThat(dashboard.revenue()).isZero();
        assertThat(dashboard.subtotal()).isZero();
        assertThat(dashboard.ordersCount()).isZero();
        assertThat(dashboard.funnel().visits()).isZero();
        // конверсии при нуле визитов = 0, а не IllegalArgumentException / деление на ноль
        // (первый шаг «visits» по определению 100%)
        assertThat(dashboard.funnel().steps()).filteredOn(step -> !"visits".equals(step.key()))
                .allSatisfy(step -> {
                    assertThat(step.conversionFromPrevious()).isZero();
                    assertThat(step.conversionFromVisits()).isZero();
                });
    }

    @Test
    void funnelShowsStepsAndConversionsFromPreviousStep() {
        when(orderRepository.allOrderLines())
                .thenReturn(List.of(Line.of(1L, Order.OrderStatus.DELIVERED, null, 0, 100.0, "a@example.com")));
        when(trackingEventRepository.countByEventType(TrackingEvent.EventType.SITE_VISIT)).thenReturn(10L);
        when(trackingEventRepository.countByEventType(TrackingEvent.EventType.PRODUCT_VIEW)).thenReturn(5L);
        when(trackingEventRepository.countByEventType(TrackingEvent.EventType.CART_ADD)).thenReturn(2L);

        AnalyticsDashboardDto dashboard = analyticsService.dashboard();

        assertThat(dashboard.funnel().visits()).isEqualTo(10L);
        assertThat(dashboard.funnel().productViews()).isEqualTo(5L);
        assertThat(dashboard.funnel().cartAdds()).isEqualTo(2L);
        assertThat(dashboard.funnel().orders()).isEqualTo(1L);

        List<StepDto> steps = dashboard.funnel().steps();
        assertThat(steps).extracting(StepDto::key)
                .containsExactly("visits", "productViews", "cartAdds", "orders");

        // 5 просмотров из 10 визитов = 50%; корзина 2 из 5 = 40% предыдущего шага и 20% от визитов;
        // заказ 1 из 2 корзин = 50% и 10% от визитов
        assertThat(steps).extracting(StepDto::conversionFromPrevious)
                .containsExactly(100.0, 50.0, 40.0, 50.0);
        assertThat(steps).extracting(StepDto::conversionFromVisits)
                .containsExactly(100.0, 50.0, 20.0, 10.0);
    }

    @Test
    void promoUsageGroupsSameCodeAndSkipsCancelledOrders() {
        when(orderRepository.allOrderLines()).thenReturn(List.of(
                // один и тот же код в разном регистре — это одна акция
                Line.of(1L, Order.OrderStatus.DELIVERED, "sale10", 10, 100.0, "a@example.com"),
                Line.of(2L, Order.OrderStatus.PROCESSING, "SALE10", 10, 400.0, "b@example.com"),
                // отменённый заказ: скидка «сгорела», в отчёт по промокодам он не идёт
                Line.of(3L, Order.OrderStatus.CANCELED, "BOOM", 50, 1000.0, "c@example.com"),
                // заказы без промокода в отчёте по акциям не участвуют вовсе
                Line.of(4L, Order.OrderStatus.DELIVERED, "  ", 0, 50.0, "d@example.com")));

        List<PromoCodeUsageDto> report = analyticsService.promoCodeUsage();

        assertThat(report).singleElement().satisfies(sale10 -> {
            assertThat(sale10.code()).isEqualTo("SALE10");
            assertThat(sale10.timesUsed()).isEqualTo(2);
            assertThat(sale10.ordersSubtotal()).isEqualTo(500.0);
            assertThat(sale10.discountAmount()).isEqualTo(50.0);
            assertThat(sale10.ordersTotal()).isEqualTo(450.0);
            assertThat(sale10.orders()).hasSize(2)
                    .allSatisfy(ref -> assertThat(ref.status()).isIn("DELIVERED", "PROCESSING"));
        });
    }

    @Test
    void promoUsageSortsByMoneySpentOnDiscount() {
        when(orderRepository.allOrderLines()).thenReturn(List.of(
                Line.of(1L, Order.OrderStatus.DELIVERED, "SALE10", 10, 1000.0, "a@example.com"),
                Line.of(2L, Order.OrderStatus.DELIVERED, "MICRO", 10, 20.0, "b@example.com")));

        List<PromoCodeUsageDto> report = analyticsService.promoCodeUsage();

        // «дорогие» по упущенной выгоде акции — наверху
        assertThat(report).extracting(PromoCodeUsageDto::code).containsExactly("SALE10", "MICRO");
        assertThat(report.get(0).discountAmount()).isEqualTo(100.0);
        assertThat(report.get(1).discountAmount()).isEqualTo(2.0);
    }

    @Test
    void mostViewedProductsComeFromRepositoryOrderAndCounter() {
        when(tShirtRepository.findMostViewed(any(Pageable.class)))
                .thenReturn(List.of(shirt(10L, "Оверсайз", 320), shirt(11L, "Аниме", 120)));

        AnalyticsDashboardDto dashboard = analyticsService.dashboard();

        assertThat(dashboard.mostViewed()).hasSize(2);
        assertThat(dashboard.mostViewed().get(0).name()).isEqualTo("Оверсайз");
        assertThat(dashboard.mostViewed().get(0).metric()).isEqualTo(320L);
        assertThat(dashboard.mostViewed().get(1).metric()).isEqualTo(120L);
    }

    @Test
    void mostFavoritedKeepsRankingEvenIfProductsComeBackUnordered() {
        when(userRepository.findFavoriteCounts(any(Pageable.class)))
                .thenReturn(List.of(new Fav(3L, 7L), new Fav(5L, 2L)));
        // repository возвращает карточки в произвольном порядке — порядок рейтинга обязан сохраниться
        when(tShirtRepository.findAllById(anyList()))
                .thenReturn(List.of(shirt(5L, "Аниме", 0), shirt(3L, "Оверсайз", 0)));

        AnalyticsDashboardDto dashboard = analyticsService.dashboard();

        assertThat(dashboard.mostFavorited()).hasSize(2);
        assertThat(dashboard.mostFavorited().get(0).id()).isEqualTo(3L);
        assertThat(dashboard.mostFavorited().get(0).metric()).isEqualTo(7L);
        assertThat(dashboard.mostFavorited().get(1).id()).isEqualTo(5L);
        assertThat(dashboard.mostFavorited().get(1).metric()).isEqualTo(2L);
    }

    @Test
    void noFavoritesDoesNotQueryProductCardsAtAll() {
        when(userRepository.findFavoriteCounts(any(Pageable.class))).thenReturn(List.of());

        AnalyticsDashboardDto dashboard = analyticsService.dashboard();

        assertThat(dashboard.mostFavorited()).isEmpty();
        // нет призов — нет и запроса карточек (лишний запрос к БД должен быть виден в тесте)
        verify(tShirtRepository, never()).findAllById(anyList());
    }

    @Test
    void dashboardReadsCountersFromRepositoriesNotFromInMemoryGuesses() {
        when(orderRepository.allOrderLines()).thenReturn(List.of(
                Line.of(1L, Order.OrderStatus.DELIVERED, null, 0, 100.0, "a@example.com")));
        when(tShirtRepository.countByIsDisabledFalse()).thenReturn(123L);
        when(userRepository.count()).thenReturn(9L);

        AnalyticsDashboardDto dashboard = analyticsService.dashboard();

        assertThat(dashboard.productsCount()).isEqualTo(123L);
        assertThat(dashboard.customersCount()).isEqualTo(9L);
    }
}
