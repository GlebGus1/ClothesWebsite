package com.example.appShop.security;

import com.example.appShop.config.JwtProperties;
import com.example.appShop.dto.TokenPair;
import com.example.appShop.entities.RefreshToken;
import com.example.appShop.exceptions.InvalidTokenException;
import com.example.appShop.repositories.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Жизненный цикл refresh-токенов: выдача, проверка, ротация, отзыв.
 * Репозиторий — мок, JwtTokenProvider настоящий (криптография тоже часть логики).
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String SECRET = "refresh-service-test-secret-refresh-service-test-secret-01234567";

    @Mock
    private RefreshTokenRepository repository;

    private JwtTokenProvider tokenProvider;
    private RefreshTokenService service;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofMinutes(15),
                Duration.ofDays(7), "appshop"));
        service = new RefreshTokenService(repository, tokenProvider);
        principal = new UserPrincipal(3L, "client@example.com", "hash", false);
    }

    @Test
    void issueStoresHashNotRawToken() {
        TokenPair pair = service.issue(principal);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        RefreshToken entity = saved.getValue();

        assertThat(pair.refreshToken()).isNotEqualTo(entity.getTokenHash());
        assertThat(entity.getTokenHash()).isEqualTo(TokenHasher.sha256(pair.refreshToken())).hasSize(64);
        assertThat(entity.getUserEmail()).isEqualTo("client@example.com");
        assertThat(entity.isRevoked()).isFalse();
        assertThat(entity.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void validRefreshReturnsEntity() {
        String raw = tokenProvider.createRefreshToken(principal);
        when(repository.findByTokenHash(TokenHasher.sha256(raw))).thenReturn(Optional.of(entity(raw, false)));

        assertThat(service.validateAndGetEntity(raw).getTokenHash())
                .isEqualTo(TokenHasher.sha256(raw));
    }

    @Test
    void accessTokenIsNotAcceptedAsRefresh() {
        String access = tokenProvider.createAccessToken(principal);

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> service.validateAndGetEntity(access))
                .withMessageContaining("другой тип");
        verify(repository, never()).findByTokenHash(any());
    }

    @Test
    void forgedTokenIsRejected() {
        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> service.validateAndGetEntity("abc.def.ghi"))
                .withMessageContaining("проверку");
    }

    @Test
    void rotatedTokenIsNotFoundAnymore() {
        String raw = tokenProvider.createRefreshToken(principal);
        when(repository.findByTokenHash(TokenHasher.sha256(raw))).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> service.validateAndGetEntity(raw))
                .withMessageContaining("не найден");
    }

    @Test
    void revokedTokenIsRejected() {
        String raw = tokenProvider.createRefreshToken(principal);
        when(repository.findByTokenHash(TokenHasher.sha256(raw))).thenReturn(Optional.of(entity(raw, true)));

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> service.validateAndGetEntity(raw))
                .withMessageContaining("отозван");
    }

    @Test
    void recordInDatabaseThatExpiredEarlierThanTokenIsRejected() {
        // TTL в базе короче, чем у самого JWT (например, админ укоротил сессию)
        JwtTokenProvider longLived = new JwtTokenProvider(new JwtProperties(SECRET, null,
                Duration.ofDays(30), "appshop"));
        service = new RefreshTokenService(repository, longLived);
        String raw = longLived.createRefreshToken(principal);

        RefreshToken expired = entity(raw, false);
        expired.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        when(repository.findByTokenHash(TokenHasher.sha256(raw))).thenReturn(Optional.of(expired));

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> service.validateAndGetEntity(raw))
                .withMessageContaining("просрочен");
    }

    @Test
    void rotationMarksOldTokenReplaced() {
        String oldRaw = tokenProvider.createRefreshToken(principal);
        RefreshToken old = entity(oldRaw, false);
        String newRaw = tokenProvider.createRefreshToken(principal);

        service.markRotated(old, newRaw);

        assertThat(old.isRevoked()).isTrue();
        assertThat(old.getReplacedBy()).isEqualTo(TokenHasher.sha256(newRaw));
        verify(repository).save(old);
    }

    @Test
    void logoutRevokesEvenExpiredToken() {
        String raw = tokenProvider.createRefreshToken(principal);
        RefreshToken stored = entity(raw, false);
        stored.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        when(repository.findByTokenHash(TokenHasher.sha256(raw))).thenReturn(Optional.of(stored));

        service.revoke(raw);

        assertThat(stored.isRevoked()).isTrue();
        verify(repository).save(stored);
    }

    @Test
    void revokeAllDelegatesToRepository() {
        when(repository.revokeAllByEmail("client@example.com")).thenReturn(4);

        assertThat(service.revokeAllForUser("client@example.com")).isEqualTo(4);
    }

    private RefreshToken entity(String rawToken, boolean revoked) {
        RefreshToken entity = new RefreshToken();
        entity.setUserEmail(principal.getUsername());
        entity.setTokenHash(TokenHasher.sha256(rawToken));
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        entity.setRevoked(revoked);
        return entity;
    }
}
