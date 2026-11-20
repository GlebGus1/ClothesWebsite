package com.example.appShop.repositories;

import com.example.appShop.entities.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Заказы конкретного пользователя (по email владельца токена), свежие — первыми. */
    Page<Order> findAllByUserEmailOrderByIdDesc(String userEmail, Pageable pageable);

    Page<Order> findAllByOrderByIdDesc(Pageable pageable);
}
