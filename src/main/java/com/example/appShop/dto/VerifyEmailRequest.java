package com.example.appShop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Запрос на подтверждение e-mail: адрес + 6-значный PIN-код из письма. */
public record VerifyEmailRequest(
        @NotBlank(message = "Укажите e-mail")
        @Email(message = "Некорректный e-mail")
        String email,

        @NotBlank(message = "Введите PIN-код")
        @Pattern(regexp = "\\d{6}", message = "PIN-код — ровно 6 цифр")
        String code
) {
}
