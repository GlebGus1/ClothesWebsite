package com.example.appShop.repositories;

import com.example.appShop.entities.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Промокоды. Все методы — производные (derived) от имён: Hibernate сам строит
 * «upper(code) = upper(?)», поэтому поиск нечувствителен к регистру и одинаково
 * работает и в MySQL, и в H2 на тестах.
 */
@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    Optional<PromoCode> findFirstByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Порядок задаёт админка: свежие коды сверху. */
    List<PromoCode> findAllByOrderByIdDesc();
}
