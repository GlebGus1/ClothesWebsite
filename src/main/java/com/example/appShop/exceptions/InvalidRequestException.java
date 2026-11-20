package com.example.appShop.exceptions;

import org.springframework.http.HttpStatus;

/** Запрос в принципе неправильный, но не по полям (400): пустая корзина, неверный формат и т.п. */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
