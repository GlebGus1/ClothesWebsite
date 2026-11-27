package com.example.appShop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Часть multipart-загрузки товара (кроме самих файлов).
 * На фронтенде это поле отправляется как FormData.append('data', new Blob([JSON.stringify(...)], {type:'application/json'})).
 */
public record TShirtFormRequest(
        @NotBlank(message = "Укажите название товара")
        String name,

        String description,

        @NotBlank(message = "Укажите размер")
        String size,

        @NotNull(message = "Укажите цену")
        @DecimalMin(value = "0.0", inclusive = false, message = "Цена должна быть больше нуля")
        Double price,

        Boolean available,

        /** Товар можно привязать к коллекции (дропу). null — «вне коллекции». */
        Long collectionId
) {
    public boolean isAvailable() {
        return available == null || available;
    }
}
