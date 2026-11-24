package com.example.appShop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * «Сработает ли мой промокод и сколько я сэкономлю».
 *
 * tshirtIds нужны, чтобы сумму считал сервер по ценам из базы, а не по ценам
 * из localStorage браузера: так фронтенд показывает честный итог, а не тот,
 * который пользователь мог подменить руками.
 */
public record PromoValidationRequest(
        @NotBlank(message = "Введите промокод")
        @Size(max = 40, message = "Промокод слишком длинный")
        String code,

        @NotEmpty(message = "Корзина пуста")
        List<Long> tshirtIds
) {
}
