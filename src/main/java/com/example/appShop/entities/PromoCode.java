package com.example.appShop.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Промокод на скидку. Таблица promo_codes создаётся Hibernate автоматически
 * (spring.jpa.hibernate.ddl-auto=update) — существующие таблицы не трогаем.
 *
 * Поля:
 *  - code           — уникальный код в верхнем регистре (его вводит покупатель);
 *  - discountPercent — целое от 1 до 99 (проверка и в БД, и в DTO-валидации);
 *  - active         — «выключенный» код не принимается, но остаётся в истории заказов;
 *  - expiresAt      — null = бессрочный; иначе код действует до этой даты.
 */
@Entity
@Table(name = "promo_codes")
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Код не принимает просроченный код, даже если админ забыл его выключить. */
    public boolean isExpiredAt(Instant moment) {
        return expiresAt != null && expiresAt.isBefore(moment);
    }

    public boolean isUsable(Instant moment) {
        return active && !isExpiredAt(moment);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(Integer discountPercent) {
        this.discountPercent = discountPercent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
