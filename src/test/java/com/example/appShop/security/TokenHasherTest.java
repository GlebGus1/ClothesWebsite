package com.example.appShop.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Хэш должен быть одинаковой длины, стабильным и разным для разных токенов. */
class TokenHasherTest {

    @Test
    void hashIsHexSha256OfKnownVector() {
        // учебный вектор SHA-256 для строки "abc" из спецификации — так проверяют корректность хэша
        assertThat(TokenHasher.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashHasFixedLengthForColumn() {
        // колонка token_hash — varchar(64) UNIQUE, длиннее помещаться не должно
        assertThat(TokenHasher.sha256("some.jwt.value")).hasSize(64);
    }

    @Test
    void sameTokenAlwaysGivesSameHash() {
        assertThat(TokenHasher.sha256("token-1")).isEqualTo(TokenHasher.sha256("token-1"));
    }

    @Test
    void oneCharacterDifferenceChangesHash() {
        assertThat(TokenHasher.sha256("token-1")).isNotEqualTo(TokenHasher.sha256("token-2"));
    }
}
