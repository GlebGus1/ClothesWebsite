package com.example.appShop.services;

import com.example.appShop.dto.ImageBinary;
import com.example.appShop.entities.Image;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.repositories.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Только чтение картинок (байты для /api/images/{id}). Изменения картинок живут в
 * TShirtService, потому что они должны быть в одной транзакции с товаром.
 * Метаданные галерее отдаёт TShirtMapper вместе с карточкой товара — отдельного
 * эндпоинта «список картинок товара» в API нет.
 *
 * GET /api/images/{id} доступен гостям: картинка в каталоге — публичный контент,
 * и <img src="..."> в браузере не умеет слать заголовок Authorization.
 */
@Service
public class ImageService {

    private final ImageRepository imageRepository;

    public ImageService(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    @Transactional(readOnly = true)
    public ImageBinary getBinary(long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Картинка", imageId));
        return new ImageBinary(
                image.getBytes(),
                image.getContentType() == null ? "application/octet-stream" : image.getContentType(),
                image.getOriginalFileName(),
                image.getSize() == null ? 0L : image.getSize());
    }
}
