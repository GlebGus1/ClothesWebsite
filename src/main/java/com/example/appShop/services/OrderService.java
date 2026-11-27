package com.example.appShop.services;

import com.example.appShop.dto.CreateOrderRequest;
import com.example.appShop.dto.OrderDto;
import com.example.appShop.dto.RestoreItemsDto;
import com.example.appShop.entities.Order;
import com.example.appShop.entities.PromoCode;
import com.example.appShop.entities.TShirt;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.mapper.OrderMapper;
import com.example.appShop.repositories.OrderRepository;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Заказы. Раньше корзина лежала в HttpSession, и заказ мог «уйти» без пользователя.
 * Теперь состав корзины приходит из запроса, а владелец определяется по токену —
 * присвоить чужой заказ невозможно.
 *
 * Важное правило магазина: каждая футболка существует в одном экземпляре, поэтому
 * оформленный заказ автоматически снимает позицию с продажи (см. create).
 */
@Service
public class OrderService {

    private static final int MAX_PAGE_SIZE = 50;

    private final OrderRepository orderRepository;
    private final TShirtRepository tShirtRepository;
    private final UserService userService;
    private final OrderMapper orderMapper;
    private final PromoCodeService promoCodeService;

    public OrderService(OrderRepository orderRepository,
                        TShirtRepository tShirtRepository,
                        UserService userService,
                        OrderMapper orderMapper,
                        PromoCodeService promoCodeService) {
        this.orderRepository = orderRepository;
        this.tShirtRepository = tShirtRepository;
        this.userService = userService;
        this.orderMapper = orderMapper;
        this.promoCodeService = promoCodeService;
    }

    @Transactional
    public OrderDto create(CreateOrderRequest request, UserPrincipal principal) {
        User user = userService.getByEmailOrThrow(principal.getUsername());

        Set<Long> requestedIds = new LinkedHashSet<>(request.tshirtIds());
        List<TShirt> tShirts = tShirtRepository.findAllById(requestedIds);

        if (tShirts.size() != requestedIds.size()) {
            throw new NotFoundException("Не все товары из корзины найдены в каталоге");
        }
        List<String> unavailable = tShirts.stream()
                .filter(tShirt -> tShirt.isDisabled() || !tShirt.isAvailable())
                .map(TShirt::getName)
                .toList();
        if (!unavailable.isEmpty()) {
            throw new InvalidRequestException("Нет в продаже: " + String.join(", ", unavailable));
        }

        Order order = new Order();
        order.setUser(user);
        order.setTshirts(tShirts);
        order.setNameOfUser(request.nameOfUser().trim());
        order.setPostOffice(request.postOffice().trim());
        order.setPostalCode(request.postalCode().trim());
        order.setPhoneNumber(request.phoneNumber().trim());
        order.setOrderStatus(Order.OrderStatus.PROCESSING);

        // Промокод проверяем на сервере: он должен существовать, быть активным и
        // не просроченным. Пустой код — просто null (скидки нет), а вот неверный код
        // показываем покупателю ошибкой, а не «молча» считаем полную сумму.
        PromoCode promoCode = promoCodeService.resolveOrNull(request.promoCode());
        if (promoCode != null) {
            // В заказ кладём строку-«слепок»: код и процент на момент покупки. Заказ
            // не должен меняться, если админ позже выключит, переименует или удалит код.
            order.setPromoCode(promoCode.getCode());
            order.setDiscountPercent(promoCode.getDiscountPercent());
        }

        Order saved = orderRepository.save(order);
        // Товар в единственном экземпляре: как только заказ принят, позицию нужно снять
        // с продажи, иначе её же купит второй покупатель. Оба действия — в одной
        // транзакции (@Transactional на методе): не было заказа — не было и списания.
        markAsSold(tShirts);

        return orderMapper.toDto(saved);
    }

    /**
     * Снимаем купленные позиции с продажи: available = false.
     * Товар не удаляется (он нужен истории заказа и карточке со статусом «продано»);
     * вернуть позицию в продажу может только админ на странице товара.
     */
    private void markAsSold(List<TShirt> tShirts) {
        tShirts.forEach(tShirt -> tShirt.setAvailable(false));
        tShirtRepository.saveAll(tShirts);
    }

    /** «Мои заказы»: фильтруем по e-mail из токена, а не по параметру от клиента. */
    @Transactional(readOnly = true)
    public Page<OrderDto> myOrders(UserPrincipal principal, int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampPageSize(pageSize));
        return orderRepository.findAllByUserEmailOrderByIdDesc(principal.getUsername(), pageable)
                .map(orderMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> allOrders(int page, int pageSize) {
        return allOrders(page, pageSize, null);
    }

    /**
     * Все заказы администратора, опционально — только с указанным статусом
     * (чипсы «Все / В обработке / В пути / Доставлен / Отменён» на странице заказов).
     * Фильтр делаем запросом к базе, а не «получили все заказы и отфильтровали в JS»:
     * иначе пагинация соврала бы (на странице 10 штук, а всего 10 000).
     */
    @Transactional(readOnly = true)
    public Page<OrderDto> allOrders(int page, int pageSize, Order.OrderStatus status) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampPageSize(pageSize));
        Page<Order> found = status == null
                ? orderRepository.findAllByOrderByIdDesc(pageable)
                : orderRepository.findAllByOrderStatusOrderByIdDesc(status, pageable);
        return found.map(orderMapper::toDto);
    }

    /**
     * Админ меняет статус; пользователь может только посмотреть свой заказ.
     *
     * ВАЖНО (правило магазина): смена статуса на CANCELED НЕ возвращает товары в продажу
     * автоматически. Причина: отменённый заказ часто означает «товар физически поехал и его
     * надо проверять» (брак, ошибка склада, возврат позже). Автоматический возврат
     * приводил бы к двойной продаже: позиция снова появлялась в каталоге, и её покупал
     * второй клиент, пока первый ещё думал, вернёт ли посылку.
     *
     * Поэтому возврат позиции в продаже — отдельное осознанное действие админа
     * (see restoreItemsToSale): кнопка + подтверждение в окне.
     */
    @Transactional
    public OrderDto updateStatus(long orderId, Order.OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Заказ", orderId));
        order.setOrderStatus(status);
        return orderMapper.toDto(order);
    }

    /**
     * Ручной «вернуть товары в продажу» из ОТМЕНЁННОГО заказа.
     *
     * Пропускаем позиции, которые нельзя честно вернуть в продажу:
     *  - снятые с продажи (is_disabled=true) — товар вообще выведен из каталога;
     *  - уже проданные в ДРУГОМ не отменённом заказе — иначе одна и та же футболка
     *    (она в одном экземпляре) оказалась бы в продаже дважды.
     * Пропущенные позиции возвращаем в ответе списком названий, чтобы админ видел,
     * почему щелчок «не сработал» для конкретной позиции.
     */
    @Transactional
    public RestoreItemsDto restoreItemsToSale(long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Заказ", orderId));

        if (order.getOrderStatus() != Order.OrderStatus.CANCELED) {
            throw new InvalidRequestException(
                    "Вернуть товары в продажу можно только из отменённого заказа (сейчас статус: "
                            + order.getOrderStatus() + ")");
        }

        List<TShirt> items = order.getTshirts() == null ? List.of() : order.getTshirts();
        if (items.isEmpty()) {
            return new RestoreItemsDto(orderMapper.toDto(order), 0, List.of());
        }

        List<Long> itemIds = items.stream().map(TShirt::getId).toList();
        Set<Long> soldElsewhere = new HashSet<>(orderRepository.findProductIdsSoldInOtherOrders(
                itemIds, orderId, Order.OrderStatus.CANCELED));

        int restored = 0;
        List<String> skipped = new ArrayList<>();
        List<TShirt> toSave = new ArrayList<>();

        for (TShirt item : items) {
            if (item.isDisabled()) {
                skipped.add(item.getName() + " (товар снят с продажи)");
                continue;
            }
            if (soldElsewhere.contains(item.getId())) {
                skipped.add(item.getName() + " (уже продан в другом заказе)");
                continue;
            }
            if (item.isAvailable()) {
                continue; // и так в продаже — не трогаем
            }
            item.setAvailable(true);
            toSave.add(item);
            restored++;
        }

        tShirtRepository.saveAll(toSave);
        return new RestoreItemsDto(orderMapper.toDto(order), restored, skipped);
    }

    @Transactional(readOnly = true)
    public OrderDto getForUser(long orderId, UserPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Заказ", orderId));
        boolean owner = order.getUser() != null
                && principal.getUsername().equals(order.getUser().getEmail());
        if (!owner && !principal.isAdmin()) {
            // Не отдаём чужой заказ, но и не говорим «такого заказа нет» — не подсказываем существование
            throw new NotFoundException("Заказ", orderId);
        }
        return orderMapper.toDto(order);
    }

    private int clampPageSize(int pageSize) {
        if (pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
