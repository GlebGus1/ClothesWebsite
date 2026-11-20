package com.example.appShop.dto;

/** Про пользователя наружу. Пароль, is_logged и verification_code — никогда. */
public record UserDto(
        long id,
        String email,
        String firstName,
        String lastName,
        boolean admin
) {
}
