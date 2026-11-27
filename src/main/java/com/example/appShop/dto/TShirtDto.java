package com.example.appShop.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Товар для списков (каталог). Картинки тут только id (без байтов): previewImageId
 * дублирует первый элемент imageIds, чтобы карточка в каталоге умела листать галерею
 * стрелочками без лишних запросов к API.
 *
 * Поля collection* нужны карточке, чтобы нарисовать бейдж коллекции и акцентную рамку
 * без отдельного запроса за коллекцией (null = товар «вне коллекции»).
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
        List<Long> imageIds,
        Long collectionId,
        String collectionName,
        String collectionBadgeColor
) {
}
