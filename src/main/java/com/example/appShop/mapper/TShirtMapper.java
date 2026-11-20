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

    public TShirtDto toDto(TShirt tShirt) {
        return new TShirtDto(
                tShirt.getId(),
                tShirt.getName(),
                tShirt.getDescription(),
                tShirt.getSize(),
                tShirt.getPrice(),
                tShirt.isAvailable(),
                tShirt.isDisabled(),
                tShirt.getDateOfCreated(),
                tShirt.getPreviewImageId());
    }

    public TShirtDetailsDto toDetailsDto(TShirt tShirt, List<Image> images) {
        return new TShirtDetailsDto(
                tShirt.getId(),
                tShirt.getName(),
                tShirt.getDescription(),
                tShirt.getSize(),
                tShirt.getPrice(),
                tShirt.isAvailable(),
                tShirt.isDisabled(),
                tShirt.getDateOfCreated(),
                tShirt.getPreviewImageId(),
                images.stream().map(this::toImageDto).toList());
    }

    public ImageDto toImageDto(Image image) {
        long tShirtId = image.gettShirt() == null ? 0L : image.gettShirt().getId();
        return new ImageDto(
                image.getId(),
                image.getName(),
                image.getOriginalFileName(),
                image.getSize() == null ? 0L : image.getSize(),
                image.getContentType(),
                image.isPreviewImage(),
                tShirtId);
    }
}
