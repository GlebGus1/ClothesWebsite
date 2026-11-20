package com.example.appShop.exceptions;

import org.springframework.http.HttpStatus;

/** Неверная пара e-mail/пароль — 401 (без подсказок, какой именно данные неверны). */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Неверный e-mail или пароль");
    }
}
