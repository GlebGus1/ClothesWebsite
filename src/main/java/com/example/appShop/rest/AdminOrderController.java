package com.example.appShop.rest;

import com.example.appShop.dto.OrderDto;
import com.example.appShop.dto.PageResponse;
import com.example.appShop.dto.UpdateOrderStatusRequest;
import com.example.appShop.entities.Order;
import com.example.appShop.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Админ-секция. Весь путь /api/admin/** закрыт ролью ADMIN в SecurityConfig,
 * поэтому внутри контроллера проверок прав уже нет.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Все подряд заказы, свежие сверху (старая страница /orders). */
    @GetMapping("/orders")
    public PageResponse<OrderDto> all(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int pageSize) {
        return PageResponse.from(orderService.allOrders(page, pageSize));
    }

    @PatchMapping("/orders/{id}/status")
    public OrderDto changeStatus(@PathVariable long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }

    /** Справочник статусов — фронтенд рисует select без хардкода. */
    @GetMapping("/orders/statuses")
    public List<String> statuses() {
        return Arrays.stream(Order.OrderStatus.values()).map(Enum::name).toList();
    }
}
