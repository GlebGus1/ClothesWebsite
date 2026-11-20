package com.example.appShop.services;

import com.example.appShop.dto.CreateOrderRequest;
import com.example.appShop.dto.OrderDto;
import com.example.appShop.entities.Order;
import com.example.appShop.entities.TShirt;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.mapper.OrderMapper;
import com.example.appShop.repositories.OrderRepository;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Заказы: проверяем безопасность (заказ всегда привязан к пользователю из токена)
 * и валидность корзины.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TShirtRepository tShirtRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private OrderService orderService;

    @Spy
    private OrderMapper orderMapper = new OrderMapper();

    private static UserPrincipal buyer() {
        return new UserPrincipal(1L, "buyer@example.com", "hash", false);
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("buyer@example.com");
        return user;
    }

    private static TShirt shirt(long id, boolean available, boolean disabled) {
        TShirt tShirt = new TShirt();
        tShirt.setId(id);
        tShirt.setName("Футболка " + id);
        tShirt.setSize("M");
        tShirt.setPrice(199.0);
        tShirt.setAvailable(available);
        tShirt.setDisabled(disabled);
        return tShirt;
    }

    private static CreateOrderRequest cart(List<Long> ids) {
        return new CreateOrderRequest(" Иван Петров ", " Отделение 12 ", " 01001 ", " +380631234567 ", ids);
    }

    @Test
    void orderIsBoundToUserFromToken() {
        when(userService.getByEmailOrThrow("buyer@example.com")).thenReturn(user());
        when(tShirtRepository.findAllById(any())).thenReturn(List.of(shirt(1L, true, false)));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(77L);
            return order;
        });

        OrderDto dto = orderService.create(cart(List.of(1L)), buyer());

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getUser().getEmail()).isEqualTo("buyer@example.com");
        assertThat(saved.getValue().getOrderStatus()).isEqualTo(Order.OrderStatus.PROCESSING);
        assertThat(dto.id()).isEqualTo(77L);
        assertThat(dto.items()).hasSize(1);
    }

    @Test
    void addressFieldsAreTrimmed() {
        when(userService.getByEmailOrThrow("buyer@example.com")).thenReturn(user());
        when(tShirtRepository.findAllById(any())).thenReturn(List.of(shirt(1L, true, false)));
        when(orderRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 78L));

        orderService.create(cart(List.of(1L)), buyer());

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getNameOfUser()).isEqualTo("Иван Петров");
        assertThat(saved.getValue().getPostOffice()).isEqualTo("Отделение 12");
        assertThat(saved.getValue().getPhoneNumber()).isEqualTo("+380631234567");
    }

    @Test
    void duplicateItemsInCartAreMerged() {
        when(userService.getByEmailOrThrow("buyer@example.com")).thenReturn(user());
        when(tShirtRepository.findAllById(any())).thenReturn(List.of(shirt(1L, true, false)));
        when(orderRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 78L));

        orderService.create(cart(List.of(1L, 1L, 1L)), buyer());

        // корзина — множество: один и тот же товар не превращается в три позиции
        verify(tShirtRepository).findAllById(new LinkedHashSet<>(List.of(1L)));
    }

    @Test
    void unknownProductStopsOrder() {
        when(userService.getByEmailOrThrow("buyer@example.com")).thenReturn(user());
        when(tShirtRepository.findAllById(any())).thenReturn(List.of());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> orderService.create(cart(List.of(999L)), buyer()));
        verify(orderRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void soldOutProductStopsOrderAndNamesIt() {
        when(userService.getByEmailOrThrow("buyer@example.com")).thenReturn(user());
        when(tShirtRepository.findAllById(any())).thenReturn(List.of(shirt(1L, false, false)));

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> orderService.create(cart(List.of(1L)), buyer()))
                .withMessageContaining("Футболка 1");
    }

    @Test
    void disabledProductStopsOrder() {
        when(userService.getByEmailOrThrow("buyer@example.com")).thenReturn(user());
        when(tShirtRepository.findAllById(any())).thenReturn(List.of(shirt(1L, true, true)));

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> orderService.create(cart(List.of(1L)), buyer()));
    }

    @Test
    void myOrdersFilterByEmailFromToken() {
        Order order = order(5L, user(), Order.OrderStatus.PROCESSING);
        when(orderRepository.findAllByUserEmailOrderByIdDesc(eq("buyer@example.com"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        Page<OrderDto> page = orderService.myOrders(buyer(), 0, 10);

        assertThat(page.getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.id()).isEqualTo(5L));
    }

    @Test
    void pageSizeIsClampedToProtectDatabase() {
        when(orderRepository.findAllByUserEmailOrderByIdDesc(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        orderService.myOrders(buyer(), -5, 9999);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllByUserEmailOrderByIdDesc(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    void adminChangesStatus() {
        Order order = order(9L, user(), Order.OrderStatus.PROCESSING);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));

        OrderDto dto = orderService.updateStatus(9L, Order.OrderStatus.IN_TRANSIT);

        assertThat(dto.status()).isEqualTo(Order.OrderStatus.IN_TRANSIT);
    }

    @Test
    void changingUnknownOrderGivesNotFound() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> orderService.updateStatus(404L, Order.OrderStatus.DELIVERED));
    }

    @Test
    void userDoesNotSeeSomeoneElsesOrder() {
        User other = new User();
        other.setId(2L);
        other.setEmail("other@example.com");
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order(9L, other, Order.OrderStatus.PROCESSING)));

        // чужой заказ отдаём как 404, чтобы не подтверждать существование чужих заказов
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> orderService.getForUser(9L, buyer()));
    }

    @Test
    void ownerSeesOwnOrder() {
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order(9L, user(), Order.OrderStatus.DELIVERED)));

        OrderDto dto = orderService.getForUser(9L, buyer());

        assertThat(dto.status()).isEqualTo(Order.OrderStatus.DELIVERED);
        assertThat(dto.customerEmail()).isEqualTo("buyer@example.com");
    }

    @Test
    void adminSeesAnyOrder() {
        User other = new User();
        other.setId(2L);
        other.setEmail("other@example.com");
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order(9L, other, Order.OrderStatus.PROCESSING)));

        UserPrincipal admin = new UserPrincipal(99L, "admin@example.com", "hash", true);

        orderService.getForUser(9L, admin);
        verify(orderRepository).findById(9L);
    }

    private static Order withId(Order order, long id) {
        order.setId(id);
        return order;
    }

    private static Order order(long id, User user, Order.OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUser(user);
        order.setOrderStatus(status);
        order.setNameOfUser("Иван Петров");
        order.setPostOffice("Отделение 12");
        order.setPostalCode("01001");
        order.setPhoneNumber("+380631234567");
        order.setTshirts(List.of(shirt(1L, true, false)));
        return order;
    }
}
