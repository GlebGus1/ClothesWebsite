package com.example.appShop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Тело запроса на вход. Записи (records) неизменяемы и отлично подходят под DTO. */
public record AuthRequest(
        @NotBlank(message = "Укажите e-mail")
        @Email(message = "Некорректный e-mail")
        String email,

        @NotBlank(message = "Укажите пароль")
        String password
) {
}
