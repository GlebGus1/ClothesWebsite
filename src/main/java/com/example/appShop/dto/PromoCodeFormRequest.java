package com.example.appShop.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Создание/правка промокода админом.
 *
 * code можно оставить пустым — тогда сервис сгенерирует случайный читаемый код
 * (см. PromoCodeService#generateCode). Регистр не важен: код нормализуем до
 * верхнего регистра, а поиск идёт без учёта регистра.
 */
public record PromoCodeFormRequest(

        @Size(max = 40, message = "Код не длиннее 40 символов")
        @Pattern(regexp = "[A-Za-z0-9\\-_]*", message = "В коде допустимы латиница, цифры, «-» и «_»")
        String code,

        @NotNull(message = "Укажите размер скидки")
        @Min(value = 1, message = "Скидка минимум 1%")
        @Max(value = 99, message = "Скидка максимум 99%")
        Integer discountPercent,

        Boolean active,

        /** null — код бессрочный. Фронтенд присылает ISO-дату (new Date().toISOString()). */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant expiresAt
) {
    public boolean isActive() {
        return active == null || active;
    }

    public String codeOrEmpty() {
        return code == null ? "" : code.trim();
    }
}
