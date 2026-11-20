package com.example.appShop.dto;

import jakarta.validation.constraints.NotBlank;

/** Запрос на обновление пары токенов и запрос на logout (отзыв refresh-токена). */
public record RefreshRequest(
        @NotBlank(message = "Нужен refresh-токен")
        String refreshToken
) {
}
