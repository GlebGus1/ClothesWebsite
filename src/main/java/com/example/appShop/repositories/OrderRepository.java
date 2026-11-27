package com.example.appShop.repositories;

import com.example.appShop.entities.Order;
import com.example.appShop.repositories.projection.OrderLineRow;
import com.example.appShop.repositories.projection.TshirtOrderRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Заказы конкретного пользователя (по email владельца токена), свежие — первыми. */
    Page<Order> findAllByUserEmailOrderByIdDesc(String userEmail, Pageable pageable);

    Page<Order> findAllByOrderByIdDesc(Pageable pageable);

    /** Фильтр по статусу для админки (ALL -> findAllByOrderByIdDesc, конкретный статус -> этот метод). */
    Page<Order> findAllByOrderStatusOrderByIdDesc(Order.OrderStatus status, Pageable pageable);

    Optional<Order> findFirstByTshirtsIdOrderByIdDesc(long tshirtId);

    /**
     * Все «строки» заказов для аналитики: один запрос вместо «каждый заказ + его позиции».
     * left join держит и заказы без позиций (например, после ручной чистки таблицы), и
     * заказы без пользователя (старые строки legacy).
     */
    @Query("select o.id as orderId, o.orderStatus as status, o.promoCode as promoCode, "
            + "o.discountPercent as discountPercent, t.price as price, u.email as customerEmail, "
            + "o.nameOfUser as nameOfUser, o.createdAt as createdAt "
            + "from Order o left join o.tshirts t left join o.user u")
    List<OrderLineRow> allOrderLines();

    /**
     * В каких заказах ушли указанные товары (для бейджа «Заказ #N» в таблице товаров).
     * ifnull(o.order_status ...) не используем: сравнение enum в JPQL безопаснее,
     * чем разбор порядкового номера в нативном SQL.
     */
    @Query("select t.id as tshirtId, o.id as orderId from Order o join o.tshirts t where t.id in :tshirtIds")
    List<TshirtOrderRow> findOrdersOfProducts(@Param("tshirtIds") List<Long> tshirtIds);

    /**
     * Какие из указанных товаров уже ушли в ДРУГИЕ заказы (кроме заказов со статусом
     * :excluded). Нужен возврату позиций из отменённого заказа: нельзя снова выставить
     * футболку, которую уже купил другой клиент.
     *
     * Статус передаём ПАРАМЕТРОМ, а не «зашитым» в JPQL: сравнение enum в JPQL по
     * литералу требует записи вида Order$OrderStatus.CANCELED, и такой текст легко
     * сломать при переименовании константы.
     */
    @Query("select distinct t.id from Order o join o.tshirts t "
            + "where o.id <> :orderId and o.orderStatus <> :excluded and t.id in :tshirtIds")
    List<Long> findProductIdsSoldInOtherOrders(@Param("tshirtIds") List<Long> tshirtIds,
                                               @Param("orderId") long orderId,
                                               @Param("excluded") Order.OrderStatus excluded);
}
