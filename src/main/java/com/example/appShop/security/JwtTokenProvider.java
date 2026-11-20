package com.example.appShop.security;

import com.example.appShop.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Создание и проверка JWT (подпись HS512, один общий секрет).
 *
 * Access-токен: короткоживущий, содержит id, e-mail и роли. Нигде не хранится —
 * сервер только проверяет подпись и срок жизни.
 * Refresh-токен: длинноживущий, есть только "подпись владельца" (email) + уникальный jti,
 * его наличие в БД проверяет RefreshTokenService.
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_ROLES = "roles";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 64) {
            throw new IllegalStateException(
                    "jwt.secret должен быть не короче 64 байт для алгоритма HS512 (сейчас " + secretBytes.length + ")");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String createAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(principal.getUsername())
                .claim(CLAIM_UID, principal.getId())
                .claim(CLAIM_ROLES, principal.getAuthorities().stream()
                        .map(Object::toString)
                        .toList())
                .claim(CLAIM_TYPE, TokenType.ACCESS.value())
                .setId(UUID.randomUUID().toString())
                .setIssuer(properties.issuerOrDefault())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(accessTtl())))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String createRefreshToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(principal.getUsername())
                .claim(CLAIM_UID, principal.getId())
                .claim(CLAIM_TYPE, TokenType.REFRESH.value())
                // jti нужен, чтобы отличать один refresh-токен от другого того же пользователя
                .setId(UUID.randomUUID().toString())
                .setIssuer(properties.issuerOrDefault())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(refreshTtl())))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Разбирает токен и возвращает claims. Бросает JwtException, если подпись не сходится,
     * токен просрочен или выпущен в будущем.
     */
    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .requireIssuer(properties.issuerOrDefault())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public TokenType tokenType(Claims claims) {
        return TokenType.from(claims.get(CLAIM_TYPE, String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> roles(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        return raw instanceof List<?> list ? (List<String>) list : List.of();
    }

    public Long userId(Claims claims) {
        Number number = claims.get(CLAIM_UID, Number.class);
        return number == null ? null : number.longValue();
    }

    public long accessExpiresInSeconds() {
        return accessTtl().getSeconds();
    }

    public Instant refreshExpirationDate() {
        return Instant.now().plus(refreshTtl());
    }

    private Duration accessTtl() {
        return properties.accessTokenTtlOrDefault();
    }

    private Duration refreshTtl() {
        return properties.refreshTokenTtlOrDefault();
    }
}
