package com.example.appShop.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Товар целиком (страница товара): те же поля + галерея картинок + счётчик просмотров. */
public record TShirtDetailsDto(
        long id,
        String name,
        String description,
        String size,
        Double price,
        boolean available,
        boolean disabled,
        LocalDateTime dateOfCreated,
        Integer previewImageId,
        List<ImageDto> images,
        Long collectionId,
        String collectionName,
        String collectionBadgeColor,
        /** Уникальные просмотры карточки (см. services/ViewCounterService.java). */
        long viewsCount
) {
}
