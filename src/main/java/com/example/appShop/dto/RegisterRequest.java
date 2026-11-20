package com.example.appShop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Укажите имя")
        @Size(max = 20, message = "Имя не длиннее 20 символов")
        String firstName,

        @NotBlank(message = "Укажите фамилию")
        @Size(max = 20, message = "Фамилия не длиннее 20 символов")
        String lastName,

        @NotBlank(message = "Укажите e-mail")
        @Email(message = "Некорректный e-mail")
        String email,

        @NotBlank(message = "Придумайте пароль")
        @Size(min = 6, max = 68, message = "Пароль от 6 до 68 символов (BCrypt обрезает длиннее)")
        String password
) {
}
