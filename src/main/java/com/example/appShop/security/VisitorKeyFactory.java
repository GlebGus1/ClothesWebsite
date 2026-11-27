package com.example.appShop.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Обезличенная метка посетителя, когда он НЕ прислал свой visitorId.
 *
 * Почему не сам IP: мы обещаем «обезличенную статистику», а IP — это персональные данные.
 * Почему не «просто хэш IP»: SHA-256 от адреса можно легко угадать обратно
 * (адресное пространство невелико), поэтому в хэш добавляем соль.
 *
 * Соль случайная и живёт только до перезапуска процесса. Это осознанно: нам незачем
 * «узнавать» одного и того же анонима неделями и уж тем более после рестарта сервера —
 * достаточно отличить одного посетителя от другого внутри текущей сессии работы сайта.
 * Постоянный идентификатор даёт браузерный visitorId (localStorage), который присылает
 * сам клиент, и он никуда на сервер не «приклеен».
 */
@Component
public class VisitorKeyFactory {

    private static final int PREFIX_LENGTH = 16;

    private final String salt;

    public VisitorKeyFactory() {
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        this.salt = HexFormat.of().formatHex(random);
    }

    public String anonymizedIp(String ip) {
        String value = ip == null ? "unknown" : ip;
        return "ip" + TokenHasher.sha256(salt + ':' + value).substring(0, PREFIX_LENGTH);
    }
}
