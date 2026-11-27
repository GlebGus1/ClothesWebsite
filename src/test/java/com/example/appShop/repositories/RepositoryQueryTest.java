package com.example.appShop.repositories;

import com.example.appShop.entities.Collection;
import com.example.appShop.entities.Order;
import com.example.appShop.entities.TShirt;
import com.example.appShop.entities.TrackingEvent;
import com.example.appShop.entities.User;
import com.example.appShop.repositories.projection.FavoriteCountRow;
import com.example.appShop.repositories.projection.OrderLineRow;
import com.example.appShop.repositories.projection.TshirtOrderRow;
import com.example.appShop.specification.TShirtSpecifications;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Проверка запросов каталога и админской аналитики на РЕАЛЬНОЙ базе (in-memory H2
 * в режиме MySQL), а не на моках.
 *
 * Зачем это нужно, если сервисы покрыты юнит-тестами: JPQL, спецификации и
 * уникальные индексы моками не проверяются. Мок «поверит» в любой текст запроса,
 * а база ответит ошибкой только при запуске. Именно здесь проверяются:
 *  - фильтр по коллекции (в том числе «коллекцию уже удалили»);
 *  - скрытые товары: витрина их не видит, админка — видит;
 *  - атомарный +1 к views_count;
 *  - уникальный индекс dedupe_key (вся защита от накрутки — на уровне базы);
 *  - запросы «в каком заказе ушёл товар» и «не продан ли он в другом заказе».
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:repo-query-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class RepositoryQueryTest {

    @Autowired
    private TShirtRepository tShirtRepository;
    @Autowired
    private CollectionRepository collectionRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrackingEventRepository trackingEventRepository;
    @Autowired
    private EntityManager entityManager;

    private Collection summer;
    private Collection autumn;
    private TShirt summerM;
    private TShirt summerL;
    private TShirt autumnM;
    private TShirt hiddenM;

    private static Collection collection(String name, String color, boolean active) {
        Collection collection = new Collection();
        collection.setName(name);
        collection.setDescription("Описание " + name);
        collection.setBadgeColor(color);
        collection.setActive(active);
        return collection;
    }

    private static TShirt shirt(String name, String size, double price, boolean available, boolean disabled) {
        TShirt tShirt = new TShirt();
        tShirt.setName(name);
        tShirt.setDescription("Состав: хлопок");
        tShirt.setSize(size);
        tShirt.setPrice(price);
        tShirt.setAvailable(available);
        tShirt.setDisabled(disabled);
        return tShirt;
    }

    @BeforeEach
    void seedCatalog() {
        summer = collectionRepository.save(collection("Summer", "#FF6B6B", true));
        autumn = collectionRepository.save(collection("Autumn", "#222222", false));

        summerM = tShirtRepository.save(shirt("Summer M", "M", 100.0, true, false));
        summerL = tShirtRepository.save(shirt("Summer L", "L", 150.0, false, false));
        autumnM = tShirtRepository.save(shirt("Autumn M", "M", 200.0, true, false));
        hiddenM = tShirtRepository.save(shirt("Hidden M", "M", 999.0, true, true));

        summerM.setCollection(summer);
        summerL.setCollection(summer);
        autumnM.setCollection(autumn);
        tShirtRepository.saveAll(List.of(summerM, summerL, autumnM));

        entityManager.flush();
        entityManager.clear();
    }

    private static List<String> namesOf(List<TShirt> shirts) {
        return shirts.stream().map(TShirt::getName).toList();
    }

    @Test
    void storefrontHidesDisabledProductsWhileAdminTableShowsThem() {
        List<TShirt> storefront = tShirtRepository
                .findAll(TShirtSpecifications.byFilters(null, null, null, false), PageRequest.of(0, 50)).getContent();
        List<TShirt> admin = tShirtRepository
                .findAll(TShirtSpecifications.byFilters(null, null, null, true), PageRequest.of(0, 50)).getContent();

        assertThat(namesOf(storefront)).containsExactlyInAnyOrder("Summer M", "Summer L", "Autumn M");
        assertThat(namesOf(admin)).contains("Hidden M");
    }

    @Test
    void catalogFiltersCombineSizeAvailabilityAndCollection() {
        assertThat(namesOf(tShirtRepository
                .findAll(TShirtSpecifications.byFilters("M", null, null, false), PageRequest.of(0, 50)).getContent()))
                .containsExactlyInAnyOrder("Summer M", "Autumn M");

        assertThat(namesOf(tShirtRepository
                .findAll(TShirtSpecifications.byFilters(null, true, summer.getId(), false), PageRequest.of(0, 50))
                .getContent())).containsExactly("Summer M");

        assertThat(namesOf(tShirtRepository
                .findAll(TShirtSpecifications.byFilters("M", null, summer.getId(), false), PageRequest.of(0, 50))
                .getContent())).containsExactly("Summer M");
    }

    @Test
    void filterByRemovedCollectionReturnsEmptyPageInsteadOfFailing() {
        // клиент мог открыть «закладку» на уже удалённую полку: INNER JOIN уронил бы запрос,
        // LEFT JOIN даёт пустую страницу
        assertThat(tShirtRepository
                .findAll(TShirtSpecifications.byFilters(null, null, 99_999L, false), PageRequest.of(0, 50))
                .getContent()).isEmpty();
    }

    @Test
    void collectionIsLoadedWithoutExtraQueriesAndSurvivesInDto() {
        List<TShirt> page = tShirtRepository
                .findAll(TShirtSpecifications.byFilters(null, null, null, false), PageRequest.of(0, 50)).getContent();

        // связь объявлена EAGER: у карточки сразу есть название и цвет бейджа
        TShirt first = page.stream().filter(t -> "Summer M".equals(t.getName())).findFirst().orElseThrow();
        assertThat(first.getCollection()).isNotNull();
        assertThat(first.getCollection().getName()).isEqualTo("Summer");
        assertThat(first.getCollection().getBadgeColor()).isEqualTo("#FF6B6B");
    }

    @Test
    void viewsCounterGrowsByAtomicUpdateEvenWithStaleCopyInSession() {
        long id = summerM.getId();

        assertThat(tShirtRepository.increaseViewsCount(id)).isEqualTo(1);
        entityManager.clear(); // как при отдельном коротком запросе из TrackingEventWriter
        tShirtRepository.increaseViewsCount(id);
        entityManager.clear();

        assertThat(tShirtRepository.findById(id).orElseThrow().getViewsCount()).isEqualTo(2L);
    }

    @Test
    void viewsCounterIgnoresUnknownProductWithoutError() {
        assertThat(tShirtRepository.increaseViewsCount(99_999L)).isZero();
    }

    @Test
    void topViewedSkipsHiddenProductsAndRespectsPageSize() {
        summerM.setViewsCount(10L);
        summerL.setViewsCount(300L);
        autumnM.setViewsCount(20L);
        hiddenM.setViewsCount(9_999L); // «накрученный» скрытый товар в топ не попадёт
        tShirtRepository.saveAll(List.of(summerM, summerL, autumnM, hiddenM));
        entityManager.flush();
        entityManager.clear();

        List<TShirt> top = tShirtRepository.findMostViewed(PageRequest.of(0, 2));

        assertThat(top).hasSize(2);
        assertThat(top.get(0).getName()).isEqualTo("Summer L");
        assertThat(top.get(1).getName()).isEqualTo("Autumn M");
    }

    @Test
    void deletingCollectionUnlinksProductsButKeepsThemAndTheirOrders() {
        long summerId = summer.getId();

        assertThat(tShirtRepository.countOfCollection(summerId)).isEqualTo(2L);
        assertThat(tShirtRepository.clearCollection(summerId)).isEqualTo(2);
        entityManager.flush();
        entityManager.clear();

        List<TShirt> stillThere = tShirtRepository.findAllById(List.of(summerM.getId(), summerL.getId()));
        assertThat(stillThere).hasSize(2);
        assertThat(stillThere).allSatisfy(tShirt -> assertThat(tShirt.getCollection()).isNull());
        assertThat(tShirtRepository.countOfCollection(summerId)).isZero();
    }

    @Test
    void productCountsPerCollectionComeFromOneGroupByQuery() {
        List<CollectionRepository.CollectionProductsRow> rows = collectionRepository.countProducts();

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getCollectionId()).isNotNull();
            assertThat(row.getProductsCount()).isPositive();
        });
    }

    @Test
    void usedCollectionIdsFilterProtectsFromDeletingInUseShelves() {
        List<Long> used = collectionRepository.findUsedCollectionIds(List.of(summer.getId(), 99_999L));

        assertThat(used).containsExactly(summer.getId());
    }

    @Test
    void onlyActiveCollectionsReachTheStorefrontBanner() {
        List<String> active = collectionRepository.findByActiveTrueOrderByIdDesc().stream()
                .map(Collection::getName).toList();

        assertThat(active).containsExactly("Summer");
        assertThat(collectionRepository.findAllByOrderByActiveDescIdDesc())
                .extracting(Collection::getName)
                .containsExactly("Summer", "Autumn");
    }

    @Test
    void sameVisitorSameDaySameProductIsStoredOnce() {
        String dedupeKey = "view:" + LocalDate.now() + ":" + summerM.getId() + ":visitor-1";
        TrackingEvent first = event(TrackingEvent.EventType.PRODUCT_VIEW, dedupeKey, summerM.getId());
        trackingEventRepository.saveAndFlush(first);

        TrackingEvent duplicate = event(TrackingEvent.EventType.PRODUCT_VIEW, dedupeKey, summerM.getId());

        // ключевая защита от накрутки живёт в базе: уникальный индекс uk_tracking_dedupe
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> trackingEventRepository.saveAndFlush(duplicate));
    }

    @Test
    void differentVisitorsDoNotCollideOnDedupeKey() {
        String today = LocalDate.now().toString();
        trackingEventRepository.saveAndFlush(event(TrackingEvent.EventType.PRODUCT_VIEW,
                "view:" + today + ":" + summerM.getId() + ":visitor-1", summerM.getId()));
        trackingEventRepository.saveAndFlush(event(TrackingEvent.EventType.PRODUCT_VIEW,
                "view:" + today + ":" + summerM.getId() + ":visitor-2", summerM.getId()));

        assertThat(trackingEventRepository.countByEventType(TrackingEvent.EventType.PRODUCT_VIEW)).isEqualTo(2L);
    }

    @Test
    void eventCountersSeparateEventTypes() {
        String today = LocalDate.now().toString();
        trackingEventRepository.saveAndFlush(event(TrackingEvent.EventType.SITE_VISIT,
                "visit:" + today + ":-:visitor-1", null));
        trackingEventRepository.saveAndFlush(event(TrackingEvent.EventType.CART_ADD,
                "cart:" + today + ":" + summerM.getId() + ":visitor-1", summerM.getId()));

        assertThat(trackingEventRepository.countByEventType(TrackingEvent.EventType.SITE_VISIT)).isEqualTo(1L);
        assertThat(trackingEventRepository.countByEventType(TrackingEvent.EventType.CART_ADD)).isEqualTo(1L);
        assertThat(trackingEventRepository.countByEventType(TrackingEvent.EventType.PRODUCT_VIEW)).isZero();
    }

    private static TrackingEvent event(TrackingEvent.EventType type, String dedupeKey, Long tshirtId) {
        TrackingEvent event = new TrackingEvent();
        event.setEventType(type);
        event.setDedupeKey(dedupeKey);
        event.setVisitorKey("visitor-1");
        event.setTshirtId(tshirtId);
        return event;
    }

    @Test
    void ordersOfProductComeBackAsPairsForTableBadge() {
        User buyer = userRepository.save(user("buyer@example.com"));
        Order firstOrder = orderRepository.save(order(buyer, Order.OrderStatus.DELIVERED, null, 0, summerM));
        Order secondOrder = orderRepository.save(order(buyer, Order.OrderStatus.PROCESSING, null, 0, summerM, autumnM));

        List<TshirtOrderRow> rows = orderRepository.findOrdersOfProducts(List.of(summerM.getId(), hiddenM.getId()));

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getTshirtId()).isNotNull();
            assertThat(row.getOrderId()).isNotNull();
        });

        // «последний» заказ для товара = максимальный id (так админ видит актуальную продажу)
        List<Long> ordersOfSummerM = rows.stream()
                .filter(row -> row.getTshirtId().equals(summerM.getId()))
                .map(TshirtOrderRow::getOrderId).toList();
        assertThat(ordersOfSummerM).containsExactlyInAnyOrder(firstOrder.getId(), secondOrder.getId());
    }

    @Test
    void productSoldInAnotherActiveOrderIsBlockedFromReturningToSale() {
        User buyer = userRepository.save(user("buyer@example.com"));
        // товар ушёл в активном заказе
        orderRepository.save(order(buyer, Order.OrderStatus.DELIVERED, null, 0, summerM));
        // и лежит в отменённом заказе, из которого мы хотим его вернуть в продажу
        Order cancelled = orderRepository.save(order(buyer, Order.OrderStatus.CANCELED, null, 0, summerM, autumnM));
        entityManager.flush();
        entityManager.clear();

        List<Long> blocked = orderRepository.findProductIdsSoldInOtherOrders(
                List.of(summerM.getId(), autumnM.getId()), cancelled.getId(), Order.OrderStatus.CANCELED);

        assertThat(blocked).containsExactly(summerM.getId());
    }

    @Test
    void productsInOtherCancelledOrdersDoNotBlockReturningToSale() {
        User buyer = userRepository.save(user("buyer@example.com"));
        orderRepository.save(order(buyer, Order.OrderStatus.CANCELED, null, 0, summerM));
        Order cancelled = orderRepository.save(order(buyer, Order.OrderStatus.CANCELED, null, 0, summerM));
        entityManager.flush();
        entityManager.clear();

        List<Long> blocked = orderRepository.findProductIdsSoldInOtherOrders(
                List.of(summerM.getId()), cancelled.getId(), Order.OrderStatus.CANCELED);

        // товар из другого ОТМЕНЁННОГО заказа свободно возвращается на полку
        assertThat(blocked).isEmpty();
    }

    @Test
    void analyticsRowsCarryOrderSumsPromoCodeAndCustomerEvenForLegacyOrderWithoutUser() {
        User buyer = userRepository.save(user("buyer@example.com"));
        Order withPromo = order(buyer, Order.OrderStatus.DELIVERED, "SALE10", 10, summerM, summerL);
        orderRepository.save(withPromo);
        // легаси-строка без покупателя и без позиций: отчёт не должен на ней упасть
        orderRepository.save(order(null, Order.OrderStatus.CANCELED, null, null));
        entityManager.flush();
        entityManager.clear();

        List<OrderLineRow> rows = orderRepository.allOrderLines();

        assertThat(rows).hasSize(3); // 2 позиции + 1 «пустой» заказ
        OrderLineRow promoRow = rows.stream()
                .filter(row -> row.getPromoCode() != null)
                .findFirst().orElseThrow();
        assertThat(promoRow.getPromoCode()).isEqualTo("SALE10");
        assertThat(promoRow.getDiscountPercent()).isEqualTo(10);
        assertThat(promoRow.getCustomerEmail()).isEqualTo("buyer@example.com");
        assertThat(promoRow.getPrice()).isIn(100.0, 150.0);

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(Order.OrderStatus.CANCELED);
            assertThat(row.getCustomerEmail()).isNull();
            assertThat(row.getPrice()).isNull();
        });
    }

    @Test
    void adminOrderListIsFilteredByStatusAndSortedNewestFirst() {
        User buyer = userRepository.save(user("buyer@example.com"));
        Order processing = orderRepository.save(order(buyer, Order.OrderStatus.PROCESSING, null, 0, summerM));
        Order canceled = orderRepository.save(order(buyer, Order.OrderStatus.CANCELED, null, 0, autumnM));
        entityManager.flush();
        entityManager.clear();

        List<Order> canceledOnly = orderRepository
                .findAllByOrderStatusOrderByIdDesc(Order.OrderStatus.CANCELED, PageRequest.of(0, 10)).getContent();
        List<Order> all = orderRepository.findAllByOrderByIdDesc(PageRequest.of(0, 10)).getContent();

        assertThat(canceledOnly).extracting(Order::getId).containsExactly(canceled.getId());
        assertThat(all).extracting(Order::getId).containsExactly(canceled.getId(), processing.getId());
    }

    @Test
    void customerSeesOnlyOwnOrdersByTheirEmail() {
        User buyer = userRepository.save(user("buyer@example.com"));
        User other = userRepository.save(user("other@example.com"));
        orderRepository.save(order(buyer, Order.OrderStatus.DELIVERED, null, 0, summerM));
        orderRepository.save(order(other, Order.OrderStatus.DELIVERED, null, 0, autumnM));
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.findAllByUserEmailOrderByIdDesc("buyer@example.com", PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(order -> assertThat(order.getUser().getEmail()).isEqualTo("buyer@example.com"));
    }

    @Test
    void latestOrderOfProductIsFoundForOrderBadge() {
        User buyer = userRepository.save(user("buyer@example.com"));
        orderRepository.save(order(buyer, Order.OrderStatus.PROCESSING, null, 0, summerM));
        Order newest = orderRepository.save(order(buyer, Order.OrderStatus.DELIVERED, null, 0, summerM));
        entityManager.flush();
        entityManager.clear();

        Optional<Order> found = orderRepository.findFirstByTshirtsIdOrderByIdDesc(summerM.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(newest.getId());
    }

    @Test
    void favoriteRankingCountsRowsOfLinkTableAndRespectsLimit() {
        User first = user("first@example.com");
        first.getFavorites().add(summerM);
        first.getFavorites().add(autumnM);
        User second = user("second@example.com");
        second.getFavorites().add(summerM);
        userRepository.saveAll(List.of(first, second));
        entityManager.flush();
        entityManager.clear();

        List<FavoriteCountRow> ranking = userRepository.findFavoriteCounts(PageRequest.of(0, 1));

        // «сердечки» считает SQL (group by), а не цикл по пользователям в Java
        assertThat(ranking).singleElement().satisfies(row -> {
            assertThat(row.getTshirtId()).isEqualTo(summerM.getId());
            assertThat(row.getFavoritesCount()).isEqualTo(2L);
        });
        assertThat(userRepository.findFavoriteCounts(PageRequest.of(0, 5))).hasSize(2);
    }

    @Test
    void bannedFlagIsStoredAndQueryableByPrimaryId() {
        User user = userRepository.save(user("spammer@example.com"));
        user.setBanned(true);
        userRepository.saveAndFlush(user);
        entityManager.clear();

        assertThat(userRepository.existsByIdAndBannedTrue(user.getId())).isTrue();
        // пользователя с таким id нет -> false, а не ошибка
        assertThat(userRepository.existsByIdAndBannedTrue(987_654L)).isFalse();
    }

    @Test
    void distinctSizesComeFromCatalogForFilterDropdown() {
        List<String> sizes = tShirtRepository.findDistinctSizes();

        // скрытый товар не должен плодить размеры в фильтре витрины
        assertThat(sizes).containsExactly("L", "M");
    }

    private static User user(String email) {
        User user = new User();
        user.setFirstName("Мария");
        user.setLastName("Иванова");
        user.setEmail(email);
        user.setPassword("hash");
        user.setEnabled(true);
        return user;
    }

    private static Order order(User user, Order.OrderStatus status, String promoCode, Integer discountPercent,
                               TShirt... items) {
        Order order = new Order();
        order.setUser(user);
        order.setOrderStatus(status);
        order.setPromoCode(promoCode);
        order.setDiscountPercent(discountPercent);
        order.setNameOfUser("Иван Петров");
        order.setPostOffice("Отделение 12");
        order.setPostalCode("01001");
        order.setPhoneNumber("+380631234567");
        order.setTshirts(List.of(items));
        return order;
    }
}
