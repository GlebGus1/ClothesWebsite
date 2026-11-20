package com.example.appShop.exceptions;

import org.springframework.http.HttpStatus;

/** Конфликт с уже существующими данными (например, e-mail занят) — 409. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
