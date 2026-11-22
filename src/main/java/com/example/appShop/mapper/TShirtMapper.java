package com.example.appShop.mapper;

import com.example.appShop.dto.ImageDto;
import com.example.appShop.dto.TShirtDetailsDto;
import com.example.appShop.dto.TShirtDto;
import com.example.appShop.entities.Image;
import com.example.appShop.entities.TShirt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entity -> DTO. Держим отдельно от сервиса, чтобы в сервисах была только бизнес-логика,
 * а список «что именно видно клиенту» был в одном месте.
 */
@Component
public class TShirtMapper {

    public TShirtDto toDto(TShirt tShirt, List<Long> imageIds) {
        return new TShirtDto(
                tShirt.getId(),
                tShirt.getName(),
                tShirt.getDescription(),
                tShirt.getSize(),
                tShirt.getPrice(),
                tShirt.isAvailable(),
                tShirt.isDisabled(),
                tShirt.getDateOfCreated(),
                previewIdOrNull(tShirt),
                imageIds == null ? List.of() : List.copyOf(imageIds));
    }

    public TShirtDetailsDto toDetailsDto(TShirt tShirt, List<Image> images) {
        List<Image> gallery = images == null ? List.of() : images;
        return new TShirtDetailsDto(
                tShirt.getId(),
                tShirt.getName(),
                tShirt.getDescription(),
                tShirt.getSize(),
                tShirt.getPrice(),
                tShirt.isAvailable(),
                tShirt.isDisabled(),
                tShirt.getDateOfCreated(),
                previewIdOrNull(tShirt),
                gallery.stream().map(image -> toImageDto(image, tShirt.getPreviewImageId())).toList());
    }

    /**
     * Флаг «это главное фото» НЕ берём из колонки images.is_preview_image — она осталась
     * от старой Thymeleaf-версии и никем не обновляется. Единственный источник истины —
     * tshirts.preview_image_id: два флага неизбежно «разъезжаются», и админка начинает
     * подсвечивать не ту картинку.
     */
    public ImageDto toImageDto(Image image, int previewImageId) {
        long tShirtId = image.gettShirt() == null ? 0L : image.gettShirt().getId();
        return new ImageDto(
                image.getId(),
                image.getName(),
                image.getOriginalFileName(),
                image.getSize() == null ? 0L : image.getSize(),
                image.getContentType(),
                image.getId() != null && image.getId() == previewImageId,
                tShirtId);
    }

    /** 0 в БД означает «обложка не выбрана», наружу отдаём null, чтобы фронтенд не рисовал /api/images/0. */
    private Integer previewIdOrNull(TShirt tShirt) {
        return tShirt.getPreviewImageId() > 0 ? tShirt.getPreviewImageId() : null;
    }
}
