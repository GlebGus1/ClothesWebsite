package com.example.appShop.dto;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.Map;

/**
 * Единый «формат ошибки» для всего API. Клиенту всегда приходит одна и та же структура,
 * поэтому React может показывать message (или поля ошибок формы) без разбора разных форматов.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fields
) {

    public static ApiError of(HttpStatusCode status, String message, String path) {
        return new ApiError(Instant.now(), status.value(), reason(status), message, path, Map.of());
    }

    public static ApiError of(HttpStatusCode status, String message, String path, Map<String, String> fields) {
        return new ApiError(Instant.now(), status.value(), reason(status), message, path,
                fields == null ? Map.of() : fields);
    }

    private static String reason(HttpStatusCode status) {
        return status instanceof HttpStatus known ? known.getReasonPhrase() : status.toString();
    }
}
