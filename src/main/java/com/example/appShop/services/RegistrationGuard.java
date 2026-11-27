package com.example.appShop.services;

import com.example.appShop.config.TurnstileProperties;
import com.example.appShop.exceptions.TooManyRequestsException;
import com.example.appShop.security.InMemoryIpRateLimiter;
import org.springframework.stereotype.Service;

/**
 * Защита самой «безобидной» на вид ручки — регистрации.
 *
 * Две независимые ступени, и обе нужны:
 *
 *  1) Лимит «сколько аккаунтов можно завести с одного IP за сутки».
 *     Капча сама по себе не спасает: бот может гонять её по кругу (или покупать решение),
 *     но жёсткий потолок «3 регистрации в сутки на адрес» делает спам-регистрации
 *     невыгодными. Лимит живёт в памяти процесса (InMemoryIpRateLimiter).
 *
 *  2) Капча Cloudflare Turnstile — уже с проверкой на нашей стороне (TurnstileService).
 *
 * Порядок именно такой: сначала дешёвый лимит (одна проверка в памяти), потом
 * внешний HTTP-запрос к Cloudflare. Если лимит выбран, мы вообще не ходим наружу.
 */
@Service
public class RegistrationGuard {

    private static final String DAILY_SCOPE = "register-daily";

    private final TurnstileService turnstileService;
    private final InMemoryIpRateLimiter rateLimiter;
    private final TurnstileProperties properties;

    public RegistrationGuard(TurnstileService turnstileService,
                            InMemoryIpRateLimiter rateLimiter,
                            TurnstileProperties properties) {
        this.turnstileService = turnstileService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public void check(String captchaToken, String clientIp) {
        int limit = properties.registrationsPerIpPerDay();
        if (limit > 0 && clientIp != null) {
            InMemoryIpRateLimiter.Decision decision =
                    rateLimiter.check(DAILY_SCOPE, clientIp, limit, properties.registrationsWindowHours());
            if (!decision.allowed()) {
                throw new TooManyRequestsException(
                        "С этого адреса слишком много попыток регистрации. Попробуйте позже.",
                        decision.retryAfterSeconds());
            }
        }
        turnstileService.verifyOrThrow(captchaToken, clientIp);
    }
}
