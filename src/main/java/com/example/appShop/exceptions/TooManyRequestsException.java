package com.example.appShop.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Превышен лимит запросов (защита от брутфорса и DDoS) — 429 Too Many Requests.
 *
 * По этому исключению ApiExceptionHandler отдаёт ещё и заголовок Retry-After:
 * браузер/бот понимает, сколько ждать, а фронтенд может показать таймер.
 * Сам фильтр лимитера отвечает без выброса исключения (см. security.RateLimitFilter) —
 * этот тип нужен сервисам, которые тоже могут «отказать» по лимиту.
 */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
