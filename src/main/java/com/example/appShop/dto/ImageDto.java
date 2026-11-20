package com.example.appShop.dto;

/** Метаданные картинки. Сами байты картинки отдаёт отдельный эндпоинт /api/images/{id}. */
public record ImageDto(
        long id,
        String name,
        String originalFileName,
        long size,
        String contentType,
        boolean previewImage,
        long tShirtId
) {
}
