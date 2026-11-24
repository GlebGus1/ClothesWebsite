package com.example.appShop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** «Пришли код ещё раз»: нужен только e-mail, пароль не запрашиваем (лишние данные — лишний риск). */
public record SendCodeRequest(
        @NotBlank(message = "Укажите e-mail")
        @Email(message = "Некорректный e-mail")
        String email
) {
}
