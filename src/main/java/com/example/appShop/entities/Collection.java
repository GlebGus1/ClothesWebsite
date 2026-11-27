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
 * Тематическая коллекция (капсула/дроп футболок): « Летний дроп », «Anime», «Оверсайз».
 *
 * Название класса — Collection (как просил заказчик). В файлах, где нужен и
 * java.util.Collection, star-import не используем: явный импорт сущности перекрывает
 * java.util.*, и читаемость кода от «Collection»-перегрузок только выигрывает.
 *
 * Таблица collections создаётся Hibernate автоматически (ddl-auto=update) —
 * существующие таблицы не меняются и не удаляются.
 */
@Entity
@Table(name = "collections")
public class Collection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название обязательно уникальное (unique = true): иначе на витрине плодятся
     * «Летний дроп» и «летний дроп», и фильтр по коллекции перестаёт работать.
     * Сравнение нечувствительно к регистру делает CollectionRepository.findBy...IgnoreCase.
     */
    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * Цвет акцента бейджа (#RRGGBB). Храним строкой, а не enum: админ выбирает
     * цвет в color-picker'е, а карточка товара красит им рамку и плашку.
     */
    @Column(name = "badge_color", length = 20)
    private String badgeColor;

    /** Только активная коллекция видна покупателям (баннер на главной, чипсы, бейджи). */
    @Column(nullable = false)
    private boolean active;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBadgeColor() {
        return badgeColor;
    }

    public void setBadgeColor(String badgeColor) {
        this.badgeColor = badgeColor;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
