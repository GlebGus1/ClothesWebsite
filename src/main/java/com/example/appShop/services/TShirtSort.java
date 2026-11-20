package com.example.appShop.services;

import org.springframework.data.domain.Sort;

/**
 * Разрешённые варианты сортировки каталога. Клиент отправляет короткую строку
 * (sort=price_desc), а мы сами решаем, какое поле реально можно сортировать —
 * так нельзя «проткнуть» сортировку по произвольному колонке.
 */
public enum TShirtSort {

    NEWEST("dateOfCreated", Sort.Direction.DESC),
    OLDEST("dateOfCreated", Sort.Direction.ASC),
    PRICE_ASC("price", Sort.Direction.ASC),
    PRICE_DESC("price", Sort.Direction.DESC),
    NAME_ASC("name", Sort.Direction.ASC);

    private final String property;
    private final Sort.Direction direction;

    TShirtSort(String property, Sort.Direction direction) {
        this.property = property;
        this.direction = direction;
    }

    public Sort toSort() {
        return Sort.by(direction, property);
    }

    public static TShirtSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEWEST;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NEWEST;
        }
    }
}
