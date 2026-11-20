package com.example.appShop.dto;

/** Внутренний результат «пары токенов» для сервисов. В JSON уходит AuthResponse. */
public record TokenPair(String accessToken, String refreshToken) {
}
