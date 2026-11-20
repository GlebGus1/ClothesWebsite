package com.example.appShop.rest;

import com.example.appShop.dto.CreateOrderRequest;
import com.example.appShop.dto.OrderDto;
import com.example.appShop.dto.PageResponse;
import com.example.appShop.security.UserPrincipal;
import com.example.appShop.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Заказы пользователя. Все «человеческие» действия: оформить, посмотреть свои,
 * посмотреть конкретный заказ (свой). Админ-операции — в AdminOrderController.
 *
 * Кто оформляет заказ, решает ТОКЕН (UserPrincipal), а не поле в запросе:
 * подставить чужой e-mail в JSON уже нельзя.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderDto> create(@Valid @RequestBody CreateOrderRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        OrderDto created = orderService.create(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me")
    public PageResponse<OrderDto> myOrders(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return PageResponse.from(orderService.myOrders(principal, page, pageSize));
    }

    @GetMapping("/{id}")
    public OrderDto getOne(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getForUser(id, principal);
    }
}
