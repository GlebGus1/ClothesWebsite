package com.example.appShop.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 в hex. Нужен, чтобы хранить в БД отпечаток refresh-токена, а не сам токен.
 *
 * Это НЕ парольный хэш (BCrypt): нам нужна не «медленность», а необратимость,
 * поэтому быстрый SHA-256 уместен — токен и так 256+ бит случайной энтропии.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 есть в любой JVM — сюда дойти невозможно
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
