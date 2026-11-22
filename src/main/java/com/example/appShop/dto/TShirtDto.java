package com.example.appShop.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Товар для списков (каталог). Картинки тут только id (без байтов): previewImageId
 * дублирует первый элемент imageIds, чтобы карточка в каталоге умела листать галерею
 * стрелочками без лишних запросов к API.
 */
public record TShirtDto(
        long id,
        String name,
        String description,
        String size,
        Double price,
        boolean available,
        boolean disabled,
        LocalDateTime dateOfCreated,
        Integer previewImageId,
        List<Long> imageIds
) {
}
