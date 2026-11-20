package com.example.appShop.exceptions;

import org.springframework.http.HttpStatus;

/** Невалидный/просроченный/отозванный токен — 401. */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
