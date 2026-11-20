package com.example.appShop.dto;

import java.time.LocalDateTime;

/** Товар для списков (каталог). Картинки тут только по id превью — никаких лишних запросов к БД. */
public record TShirtDto(
        long id,
        String name,
        String description,
        String size,
        Double price,
        boolean available,
        boolean disabled,
        LocalDateTime dateOfCreated,
        Integer previewImageId
) {
}
