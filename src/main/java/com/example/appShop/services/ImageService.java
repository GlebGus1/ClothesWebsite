package com.example.appShop.services;

import com.example.appShop.dto.ImageBinary;
import com.example.appShop.dto.ImageDto;
import com.example.appShop.entities.Image;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.mapper.TShirtMapper;
import com.example.appShop.repositories.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Только чтение картинок (метаданные и байты). Изменения картинок живут в TShirtService,
 * потому что они должны быть в одной транзакции с товаром.
 *
 * GET /api/images/{id} доступен гостям: картинка в каталоге — публичный контент,
 * и <img src="..."> в браузере не умеет слать заголовок Authorization.
 */
@Service
public class ImageService {

    private final ImageRepository imageRepository;
    private final TShirtMapper tShirtMapper;

    public ImageService(ImageRepository imageRepository, TShirtMapper tShirtMapper) {
        this.imageRepository = imageRepository;
        this.tShirtMapper = tShirtMapper;
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

    @Transactional(readOnly = true)
    public List<ImageDto> listByTShirt(long tShirtId) {
        return imageRepository.findAllByTShirt(tShirtId).stream()
                .map(tShirtMapper::toImageDto)
                .toList();
    }
}
