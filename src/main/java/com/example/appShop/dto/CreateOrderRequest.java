package com.example.appShop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Оформление заказа: корзина теперь живёт на фронтенде, поэтому
 * клиент присылает список id товаров, а сервер сам проверяет их наличие в БД.
 */
public record CreateOrderRequest(
        @NotBlank(message = "Укажите имя получателя")
        String nameOfUser,

        @NotBlank(message = "Укажите отделение получения")
        String postOffice,

        @NotBlank(message = "Укажите почтовый индекс")
        @Pattern(regexp = "\\d{4,10}", message = "Индекс — от 4 до 10 цифр")
        String postalCode,

        @NotBlank(message = "Укажите телефон")
        @Pattern(regexp = "\\+?\\d{7,15}", message = "Телефон — от 7 до 15 цифр (можно с +)")
        String phoneNumber,

        @NotEmpty(message = "Корзина пуста")
        List<Long> tshirtIds,

        /**
         * Промокод по желанию: null/пустая строка — без скидки. Сервер проверяет код
         * сам (активен, не просрочен, существует), клиенту итог не доверяем.
         */
        @Size(max = 40, message = "Промокод слишком длинный")
        String promoCode
) {
}
