package com.example.appShop.mapper;

import com.example.appShop.dto.OrderDto;
import com.example.appShop.dto.TotalsDto;
import com.example.appShop.entities.Order;
import com.example.appShop.entities.TShirt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entity Order -> OrderDto.
 *
 * ВАЖНО: вызывать только внутри транзакционной границы сервиса, иначе
 * order.getTshirts() и order.getUser() — ленивые связи, и вне сессии Hibernate
 * бросит LazyInitializationException (мы специально включили spring.jpa.open-in-view=false).
 */
@Component
public class OrderMapper {

    public OrderDto toDto(Order order) {
        List<OrderDto.Item> items = order.getTshirts() == null
                ? List.of()
                : order.getTshirts().stream()
                .map(this::toItem)
                .toList();

        // Итог считаем по ценам заказа, а не по тем, что «прислал» браузер.
        double subtotal = items.stream().mapToDouble(item -> item.price() == null ? 0.0 : item.price()).sum();
        TotalsDto totals = TotalsDto.of(subtotal, order.getDiscountPercent());

        return new OrderDto(
                order.getId(),
                order.getOrderStatus(),
                order.getNameOfUser(),
                order.getPostOffice(),
                order.getPostalCode(),
                order.getPhoneNumber(),
                order.getCreatedAt(),
                order.getUser() == null ? null : (long) order.getUser().getId(),
                order.getUser() == null ? null : order.getUser().getEmail(),
                order.getPromoCode(),
                totals.discountPercent(),
                totals.discountAmount(),
                totals.total(),
                items);
    }

    private OrderDto.Item toItem(TShirt tShirt) {
        Integer preview = tShirt.getPreviewImageId() > 0 ? tShirt.getPreviewImageId() : null;
        return new OrderDto.Item(tShirt.getId(), tShirt.getName(), tShirt.getSize(), tShirt.getPrice(), preview);
    }
}
