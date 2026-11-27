package com.example.appShop.dto;

/**
 * Про пользователя наружу. Пароль, is_logged и verification_code — никогда.
 *
 * enabled — «подтверждён ли e-mail», banned — «заблокирован ли аккаунт админом».
 * Оба поля нужны таблице «Пользователи» в админке, чтобы показать статус и кнопку бана.
 */
public record UserDto(
        long id,
        String email,
        String firstName,
        String lastName,
        boolean admin,
        boolean enabled,
        boolean banned
) {
}
