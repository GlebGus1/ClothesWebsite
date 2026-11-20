package com.example.appShop.exceptions;

import org.springframework.http.HttpStatus;

/** Сущности нет в базе (404). */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public NotFoundException(String entity, Object id) {
        this(entity + " с id=" + id + " не найден");
    }
}
