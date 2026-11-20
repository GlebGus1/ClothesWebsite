package com.example.appShop.security;

import com.example.appShop.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Тесты подписи и разбора JWT. Здесь нет Mockito: JwtTokenProvider — чистый код,
 * ему нужны только настройки (секрет, сроки, issuer).
 */
class JwtTokenProviderTest {

    /** Ровно 64+ символа: HS512 отказывается работать с коротким секретом. */
    private static final String SECRET = "unit-test-secret-unit-test-secret-unit-test-secret-0123456789-abcdefgh";

    private static JwtProperties properties(Duration accessTtl, String issuer) {
        return new JwtProperties(SECRET, accessTtl, Duration.ofDays(7), issuer);
    }

    private static UserPrincipal user() {
        return new UserPrincipal(7L, "buyer@example.com", "hash", false);
    }

    @Test
    void accessContainsIdentityAndRoles() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(Duration.ofMinutes(15), "appshop"));

        Claims claims = provider.parseClaims(provider.createAccessToken(user()));

        assertThat(claims.getSubject()).isEqualTo("buyer@example.com");
        assertThat(provider.userId(claims)).isEqualTo(7L);
        assertThat(provider.tokenType(claims)).isEqualTo(TokenType.ACCESS);
        assertThat(provider.roles(claims)).containsExactly(Roles.USER);
        assertThat(claims.getIssuer()).isEqualTo("appshop");
    }

    @Test
    void adminPrincipalGetsBothRoles() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(null, null));

        Claims claims = provider.parseClaims(
                provider.createAccessToken(new UserPrincipal(1L, "admin@example.com", "hash", true)));

        // Админ inherits роль пользователя — так требования ".hasRole(USER)" работают и для админа
        assertThat(provider.roles(claims)).containsExactlyInAnyOrder(Roles.USER, Roles.ADMIN);
        assertThat(provider.tokenType(claims)).isEqualTo(TokenType.ACCESS);
    }

    @Test
    void refreshHasNoRolesAndItsOwnType() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(null, null));

        Claims claims = provider.parseClaims(provider.createRefreshToken(user()));

        assertThat(provider.tokenType(claims)).isEqualTo(TokenType.REFRESH);
        assertThat(provider.roles(claims)).isEmpty();
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void twoRefreshTokensForSameUserAreDifferent() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(null, null));

        String first = provider.createRefreshToken(user());
        String second = provider.createRefreshToken(user());

        // разные jti нужны, чтобы ротацию можно было отслеживать по конкретному токену
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtTokenProvider issuer = new JwtTokenProvider(properties(null, null));
        JwtTokenProvider other = new JwtTokenProvider(
                new JwtProperties(SECRET + "XXXXX-extra-secret", null, null, null));

        String foreign = other.createAccessToken(user());

        assertThatExceptionOfType(JwtException.class).isThrownBy(() -> issuer.parseClaims(foreign));
    }

    @Test
    void expiredTokenThrowsSpecificException() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(Duration.ofMillis(-1000), "appshop"));

        String token = provider.createAccessToken(user());

        // по этому исключению фронтенд и отличает «нужен refresh» от «токен фальшивый»
        assertThatExceptionOfType(ExpiredJwtException.class).isThrownBy(() -> provider.parseClaims(token));
    }

    @Test
    void defaultsAreUsedWhenTtlNotConfigured() {
        JwtTokenProvider provider = new JwtTokenProvider(properties(null, "  "));

        assertThat(provider.accessExpiresInSeconds()).isEqualTo(15 * 60);
        assertThat(provider.refreshExpirationDate()).isAfter(java.time.Instant.now().plus(Duration.ofDays(6)));
        assertThat(provider.parseClaims(provider.createAccessToken(user())).getIssuer()).isEqualTo("appshop");
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new JwtTokenProvider(new JwtProperties("too-short", null, null, null)));
    }
}
