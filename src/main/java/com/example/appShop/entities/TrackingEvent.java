package com.example.appShop.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Событие поведенческой аналитики (только обезличенное!).
 *
 * Мы НЕ храним e-mail, имя, cookie-идентификатор пользователя или полный IP —
 * только короткий ключ посетителя, который фронтенд сгенерировал сам (crypto.randomUUID)
 * и который живёт в его localStorage. Это честный ответ на требование
 * «обезличенная статистика посещений» + согласие в cookie-баннере.
 *
 * Защита от накрутки за счёт dedupe_key: уникальная пара
 * «тип события + ключ посетителя + товар + дата». Повторное открытие той же
 * карточки в тот же день (F5, возврат со страницы заказа) вторым рядом не появится.
 * Уникальный индекс запрещает дубликаты на уровне базы, а не «на честном слове».
 */
@Entity
@Table(name = "tracking_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_tracking_dedupe", columnNames = "dedupe_key"),
        indexes = {
                @Index(name = "idx_tracking_type", columnList = "event_type"),
                @Index(name = "idx_tracking_created", columnList = "created_at")
        })
public class TrackingEvent {

    /** Тип события: именно по ним строится воронка визит -> просмотр -> корзина -> заказ. */
    public enum EventType {
        SITE_VISIT,
        PRODUCT_VIEW,
        CART_ADD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    /** Обезличенный ключ посетителя (uuid из localStorage) или короткая строка на основе IP. */
    @Column(name = "visitor_key", nullable = false, length = 64)
    private String visitorKey;

    @Column(name = "dedupe_key", nullable = false, length = 160)
    private String dedupeKey;

    /** Для PRODUCT_VIEW / CART_ADD — о каком товаре речь; для визита сайта — null. */
    @Column(name = "tshirt_id")
    private Long tshirtId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getVisitorKey() {
        return visitorKey;
    }

    public void setVisitorKey(String visitorKey) {
        this.visitorKey = visitorKey;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public Long getTshirtId() {
        return tshirtId;
    }

    public void setTshirtId(Long tshirtId) {
        this.tshirtId = tshirtId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
