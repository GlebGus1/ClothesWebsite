package com.example.appShop.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Базовое исключение бизнес-логики. В себе держит HTTP-статус, которым оно
 * должно «отзываться» в ответе API. Обрабатывает его ApiExceptionHandler.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
