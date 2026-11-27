package com.example.appShop.services;

import com.example.appShop.config.RateLimitProperties;
import com.example.appShop.config.TurnstileProperties;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.exceptions.TooManyRequestsException;
import com.example.appShop.security.InMemoryIpRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Защита регистрации: лимит «аккаунтов с одного IP за сутки» + капча.
 *
 * Порядок ступеней важен не меньше самих лимитов: сначала дешёвая проверка в памяти,
 * и только потом внешний запрос к Cloudflare. Иначе бот заставил бы нас каждый раз
 * гонять HTTP наружу, и отказ пришёл бы поздно и дорого.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationGuardTest {

    @Mock
    private TurnstileService turnstileService;

    private static TurnstileProperties props(int registrationsPerIpPerDay) {
        return new TurnstileProperties("0xSITE", "0xSECRET", "https://example.invalid/siteverify",
                3000, registrationsPerIpPerDay, 24);
    }

    private static RegistrationGuard guard(TurnstileService turnstileService,
                                           InMemoryIpRateLimiter limiter,
                                           TurnstileProperties properties) {
        return new RegistrationGuard(turnstileService, limiter, properties);
    }

    private static InMemoryIpRateLimiter limiter() {
        return new InMemoryIpRateLimiter(new RateLimitProperties(true, 80, 5, 1, 10, false));
    }

    @Test
    void fourthRegistrationFromSameIpInOneDayIsRefused() {
        RegistrationGuard guard = guard(turnstileService, limiter(), props(3));

        for (int attempt = 1; attempt <= 3; attempt++) {
            final int number = attempt;
            assertThatCode(() -> guard.check("token-" + number, "203.0.113.7"))
                    .as("регистрация №%d с одного адреса", number)
                    .doesNotThrowAnyException();
        }

        assertThatExceptionOfType(TooManyRequestsException.class)
                .isThrownBy(() -> guard.check("token-4", "203.0.113.7"))
                .satisfies(error -> assertThat(error.getRetryAfterSeconds()).isPositive());
    }

    @Test
    void captchaIsNotCalledOnceLimitIsExhausted() {
        RegistrationGuard guard = guard(turnstileService, limiter(), props(1));

        guard.check("token-1", "203.0.113.7");
        assertThatExceptionOfType(TooManyRequestsException.class)
                .isThrownBy(() -> guard.check("token-2", "203.0.113.7"));

        // дешёвая ступень отработала первой: внешнего обращения к Cloudflare не было
        verify(turnstileService, times(1)).verifyOrThrow(eq("token-1"), eq("203.0.113.7"));
        verify(turnstileService, never()).verifyOrThrow(eq("token-2"), any());
    }

    @Test
    void dailyLimitIsCountedPerIp() {
        RegistrationGuard guard = guard(turnstileService, limiter(), props(1));

        guard.check("token-a", "203.0.113.7");
        guard.check("token-b", "198.51.100.4");

        // соседний адрес (другой провайдер/сосед по NAT-подсети решает свою квоту)
        verify(turnstileService).verifyOrThrow("token-a", "203.0.113.7");
        verify(turnstileService).verifyOrThrow("token-b", "198.51.100.4");
    }

    @Test
    void captchaCheckHappensEvenWhenLimitAllows() {
        RegistrationGuard guard = guard(turnstileService, limiter(), props(10));

        guard.check("token", "203.0.113.7");

        // капча — обязательная ступень: лимит её не заменяет
        verify(turnstileService).verifyOrThrow("token", "203.0.113.7");
    }

    @Test
    void captchaFailureIsPropagatedAsIs() {
        RegistrationGuard guard = guard(turnstileService, limiter(), props(10));
        doThrow(new InvalidRequestException("Капча не пройдена"))
                .when(turnstileService).verifyOrThrow("bad", "203.0.113.7");

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> guard.check("bad", "203.0.113.7"))
                .withMessage("Капча не пройдена");
    }

    @Test
    void zeroLimitMeansOnlyCaptchaProtectsRegistration() {
        InMemoryIpRateLimiter realLimiter = limiter();
        RegistrationGuard guard = guard(turnstileService, realLimiter, props(0));

        for (int attempt = 0; attempt < 10; attempt++) {
            guard.check("token", "203.0.113.7");
        }

        // лимит выключён настройкой => счётчик не расходуется, капча вызвана каждый раз
        assertThat(realLimiter.currentHits("register-daily", "203.0.113.7")).isZero();
        verify(turnstileService, times(10)).verifyOrThrow("token", "203.0.113.7");
    }

    @Test
    void requestWithoutIdentifiableIpIsNotCountedInOneSharedBucket() {
        InMemoryIpRateLimiter realLimiter = limiter();
        RegistrationGuard guard = guard(turnstileService, realLimiter, props(1));

        // если IP не определён, «общая корзина» заблокировала бы всех подряд
        guard.check("token-1", null);
        assertThatCode(() -> guard.check("token-2", null)).doesNotThrowAnyException();

        verify(turnstileService, times(2)).verifyOrThrow(any(), any());
    }
}
