package com.example.appShop.dto;

import jakarta.validation.constraints.NotNull;

/** Быстрая смена статуса наличия прямо из таблицы «Товары (админ)». */
public record AvailabilityRequest(
        @NotNull(message = "Укажите статус наличия (true/false)")
        Boolean available
) {
    public boolean isAvailable() {
        return Boolean.TRUE.equals(available);
    }
}
