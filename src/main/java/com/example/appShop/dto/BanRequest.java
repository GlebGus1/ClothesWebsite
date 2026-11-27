package com.example.appShop.dto;

import jakarta.validation.constraints.NotNull;

/** Запрос «заблокировать / разблокировать» пользователя (админка, вкладка «Пользователи»). */
public record BanRequest(
        @NotNull(message = "Укажите, блокируем или разблокируем (true/false)")
        Boolean banned
) {
    public boolean isBanned() {
        return Boolean.TRUE.equals(banned);
    }
}
