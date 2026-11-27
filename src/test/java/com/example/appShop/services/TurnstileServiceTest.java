package com.example.appShop.services;

import com.example.appShop.config.TurnstileProperties;
import com.example.appShop.exceptions.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Капча Cloudflare Turnstile на регистрации.
 *
 * Реальных обращений к Cloudflare в тестах нет: подменяем только method send()
 * (он package-private ровно для этого).
 */
class TurnstileServiceTest {

    private static TurnstileProperties off() {
        return new TurnstileProperties("", "", "https://example.invalid/siteverify", 3000, 3, 24);
    }

    private static TurnstileProperties on() {
        return new TurnstileProperties("0xSITE", "0xSECRET", "https://example.invalid/siteverify", 3000, 3, 24);
    }

    /** Замена внешнего HTTP-сервиса: фиксируем, СКОЛЬКО раз и с каким токеном пошли наружу. */
    private static final class StubbedService extends TurnstileService {

        private int calls;
        private TurnstileService.TurnstileVerifyResponse answer =
                new TurnstileService.TurnstileVerifyResponse(true, List.of());
        private RuntimeException failure;

        private StubbedService(TurnstileProperties properties) {
            super(properties);
        }

        @Override
        TurnstileService.TurnstileVerifyResponse send(String captchaToken, String clientIp) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return answer;
        }
    }

    @Test
    void captchaIsOffWhenSecretKeyIsNotConfigured() {
        TurnstileService service = new TurnstileService(off());

        assertThat(service.status().required()).isFalse();
        assertThat(service.status().message()).contains("TURNSTILE_SECRET_KEY");

        // локальная разработка и CI: регистрация проходит без капчи и без обращений наружу
        assertThatCode(() -> service.verifyOrThrow(null, "203.0.113.7")).doesNotThrowAnyException();
    }

    @Test
    void statusExposesPublicSiteKeyWhenCaptchaIsRequired() {
        TurnstileService service = new TurnstileService(on());

        assertThat(service.status().required()).isTrue();
        // публичный ключ можно отдать браузеру, секретный — никогда
        assertThat(service.status().siteKey()).isEqualTo("0xSITE");
        assertThat(service.status().message()).isNull();
    }

    @Test
    void missingTokenIsRejectedWithoutCallingCloudflare() {
        StubbedService service = new StubbedService(on());

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.verifyOrThrow("  ", "203.0.113.7"))
                .withMessageContaining("не робот");

        assertThat(service.calls).isZero();
    }

    @Test
    void rejectedTokenStopsRegistration() {
        StubbedService service = new StubbedService(on());
        service.answer = new TurnstileService.TurnstileVerifyResponse(false, List.of("bad-request"));

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.verifyOrThrow("forged-token", "203.0.113.7"))
                .withMessageContaining("Капча не пройдена");

        assertThat(service.calls).isEqualTo(1);
    }

    @Test
    void nullResponseIsTreatedAsFailure() {
        StubbedService service = new StubbedService(on());
        service.answer = null;

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.verifyOrThrow("token", "203.0.113.7"));
    }

    @Test
    void cloudflareOutageBlocksRegistrationInsteadOfSilentlyPassing() {
        StubbedService service = new StubbedService(on());
        service.failure = new IllegalStateException("connect timed out");

        // fail-closed: если защиту проверить нельзя, регистрацию не проводим
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.verifyOrThrow("token", "203.0.113.7"))
                .withMessageContaining("защиты от роботов недоступен");
    }

    @Test
    void passedTokenLetsRegistrationContinue() {
        StubbedService service = new StubbedService(on());

        assertThatCode(() -> service.verifyOrThrow("good-token", "203.0.113.7")).doesNotThrowAnyException();
        assertThat(service.calls).isEqualTo(1);
    }
}
