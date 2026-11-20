package com.example.appShop.dto;

/** Ответ авторизации: обе пары токенов + профиль, чтобы фронтенд не делал второй запрос. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {
    public static final String TYPE = "Bearer";
}
