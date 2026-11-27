package com.example.appShop.rest;

import com.example.appShop.dto.OrderDto;
import com.example.appShop.dto.PageResponse;
import com.example.appShop.dto.RestoreItemsDto;
import com.example.appShop.dto.UpdateOrderStatusRequest;
import com.example.appShop.entities.Order;
import com.example.appShop.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Админ-секция заказов. Весь путь /api/admin/** закрыт ролью ADMIN в SecurityConfig,
 * поэтому внутри контроллера проверок прав уже нет.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Заказы, свежие сверху. Параметр status — фильтр по статусу:
     * без него (или status=ALL/пусто) показываем ВСЕ заказы, иначе — только выбранный статус.
     * Фильтр выполняет база, поэтому пагинация остаётся честной.
     */
    @GetMapping("/orders")
    public PageResponse<OrderDto> all(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      @RequestParam(required = false) String status) {
        return PageResponse.from(orderService.allOrders(page, pageSize, parseStatus(status)));
    }

    @PatchMapping("/orders/{id}/status")
    public OrderDto changeStatus(@PathVariable long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }

    /**
     * Сознательный возврат позиций отменённого заказа в продажу (available = true).
     *
     * Это ОТДЕЛЬНАЯ операция, а не побочный эффект смены статуса на CANCELED:
     * отмена заказа не означает автоматически, что футболка снова свободна.
     * Фронтенд спрашивает подтвердение (window.confirm) перед вызовом — см. AdminOrdersPage.
     */
    @PostMapping("/orders/{id}/restore-items")
    public RestoreItemsDto restoreItems(@PathVariable long id) {
        return orderService.restoreItemsToSale(id);
    }

    /** Справочник статусов — фронтенд рисует select и чипсы фильтра без хардкода. */
    @GetMapping("/orders/statuses")
    public List<String> statuses() {
        return Arrays.stream(Order.OrderStatus.values()).map(Enum::name).toList();
    }

    /**
     * «ALL» и пустая строка = без фильтра. Некорректное значение отдаст 400:
     * Spring не умеет превратить неизвестную строку в enum, а мы не «подменяем» её на
     * PROCESSING — иначе админ увидел бы не те заказы, думая, что фильтрует.
     */
    private Order.OrderStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank() || "ALL".equalsIgnoreCase(rawStatus)) {
            return null;
        }
        return Order.OrderStatus.valueOf(rawStatus.trim().toUpperCase());
    }
}
