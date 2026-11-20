package com.example.appShop.security;

import com.example.appShop.dto.TokenPair;
import com.example.appShop.entities.RefreshToken;
import com.example.appShop.exceptions.InvalidTokenException;
import com.example.appShop.repositories.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Жизненный цикл refresh-токенов: выдача, проверка, ротация, отзыв.
 *
 * Схема:
 *  1) логин  -> issue(): access + refresh, хэш refreshа летит в таблицу refresh_tokens;
 *  2) refresh -> validate(): подпись + срок + поиск хэша в БД + revoked=false,
 *                затем revoke(старый) + issue(новый) = ротация;
 *  3) logout -> revoke(): запис revoked=true, дальше этот refresh больше не примут.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtTokenProvider tokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public TokenPair issue(UserPrincipal principal) {
        String refreshToken = tokenProvider.createRefreshToken(principal);
        String accessToken = tokenProvider.createAccessToken(principal);

        RefreshToken entity = new RefreshToken();
        entity.setUserEmail(principal.getUsername());
        entity.setTokenHash(TokenHasher.sha256(refreshToken));
        entity.setExpiresAt(tokenProvider.refreshExpirationDate());
        entity.setCreatedAt(Instant.now());
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);

        return new TokenPair(accessToken, refreshToken);
    }

    /** Возвращает запись в БД, если токен настоящий, живой и не отозванный. */
    @Transactional(readOnly = true)
    public RefreshToken validateAndGetEntity(String rawRefreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parseClaims(rawRefreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Refresh-токен не прошёл проверку подписи или срока действия");
        }
        if (tokenProvider.tokenType(claims) != TokenType.REFRESH) {
            throw new InvalidTokenException("Ожидывался refresh-токен, передан другой тип токена");
        }

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh-токен не найден (возможно, он уже заменён новым)"));

        if (!stored.isActive()) {
            throw new InvalidTokenException(stored.isRevoked()
                    ? "Refresh-токен отозван"
                    : "Refresh-токен просрочен");
        }
        return stored;
    }

    /** Ротация: старый токен помечаем отозванным и указываем, чем заменён. */
    @Transactional
    public void markRotated(RefreshToken oldToken, String newRawRefreshToken) {
        oldToken.setRevoked(true);
        oldToken.setReplacedBy(TokenHasher.sha256(newRawRefreshToken));
        refreshTokenRepository.save(oldToken);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        RefreshToken stored = validateAndGetEntityAllowExpired(rawRefreshToken);
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
    }

    @Transactional
    public int revokeAllForUser(String email) {
        return refreshTokenRepository.revokeAllByEmail(email);
    }

    @Transactional
    public int cleanupExpired() {
        int removed = refreshTokenRepository.deleteAllExpired(Instant.now());
        if (removed > 0) {
            log.info("Удалено просроченных refresh-токенов: {}", removed);
        }
        return removed;
    }

    /**
     * Для logout строгая проверка «живости» не нужна: отозвать надо и просроченный токен.
     * Подпись при этом всё равно проверяется, чтобы никто не «отозвал» чужие записи.
     */
    private RefreshToken validateAndGetEntityAllowExpired(String rawRefreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parseClaims(rawRefreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Refresh-токен не прошёл проверку подписи");
        }
        if (tokenProvider.tokenType(claims) != TokenType.REFRESH) {
            throw new InvalidTokenException("Ожидывался refresh-токен, передан другой тип токена");
        }
        return refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh-токен не найден"));
    }
}
