package com.example.appShop.dto;

/**
 * Ответ регистрации / повторной отправки PIN-кода.
 *
 * Токенов здесь НЕТ намеренно: пока e-mail не подтверждён, выпускать access- и
 * refresh-токено нельзя — иначе неподтверждённый аккаунт получил бы доступ к API.
 * Фронтенд по этому ответу показывает форму ввода PIN-кода.
 *
 * requiresVerification=false означает, что подтверждение выключено конфигом
 * (app.verification.enabled=false) — тогда можно сразу идти на вход.
 */
public record VerificationStatusDto(
        String email,
        boolean requiresVerification,
        String message
) {
}
