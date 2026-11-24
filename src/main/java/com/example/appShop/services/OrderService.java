package com.example.appShop.services;

import com.example.appShop.dto.CreateOrderRequest;
import com.example.appShop.dto.OrderDto;
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
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampPageSize(pageSize));
        return orderRepository.findAllByOrderByIdDesc(pageable).map(orderMapper::toDto);
    }

    /** Админ меняет статус; пользователь может только посмотреть свой заказ. */
    @Transactional
    public OrderDto updateStatus(long orderId, Order.OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Заказ", orderId));
        order.setOrderStatus(status);
        return orderMapper.toDto(order);
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
