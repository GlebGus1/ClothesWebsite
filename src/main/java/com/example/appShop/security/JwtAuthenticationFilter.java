package com.example.appShop.security;

import com.example.appShop.exceptions.AccountBannedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Единственный фильтр, который превращает JWT в «аутентификацию Spring Security».
 *
 * Почему OncePerRequestFilter: гарантируется ровно один вызов на запрос
 * (даже при FORWARD/ERROR фильтр не сработает второй раз).
 *
 * Порядок работы:
 *   1) достали заголовок "Authorization: Bearer ...";
 *   2) проверили подпись/срок и тип токена (только ACCESS!);
 *   3) проверили, не попал ли пользователь в чёрный список (один запрос по id —
 *      иначе забаненный человек спокойно ходил бы с токеном ещё 15 минут);
 *   4) собрали UserPrincipal + роли из claims и положили в SecurityContextHolder;
 *   5) пошли дальше по цепочке. Сессий и куки по-прежнему нет.
 *
 * Если токен плохой — мы НЕ отдаём ошибку сами. Контекст остаётся пустым, а
 * ExceptionTranslationFilter + RestAuthenticationEntryPoint решат, что это 401
 * (или 403, если запрос помечен как «заблокированный аккаунт»).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final AccountStatusGuard accountStatusGuard;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AccountStatusGuard accountStatusGuard) {
        this.tokenProvider = tokenProvider;
        this.accountStatusGuard = accountStatusGuard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = tokenProvider.parseClaims(token);

                if (tokenProvider.tokenType(claims) != TokenType.ACCESS) {
                    //refresh-токен в качестве access — типичная ошибка клиента
                    reject(request, "В Authorization должен быть access-токен, а не refresh-токен");
                } else if (accountStatusGuard.isBanned(userIdOf(claims))) {
                    // Токен выпущен ДО бана (он живёт 15 минут), а доступа у пользователя уже нет.
                    // Здесь мы НЕ бросаем исключение: оно ушло бы мимо ExceptionTranslationFilter
                    // в 500-ю страницу контейнера. Вместо этого ставим атрибут, и
                    // RestAuthenticationEntryPoint отдаёт честный 403 «Аккаунт заблокирован».
                    rejectForbidden(request, AccountBannedException.MESSAGE);
                } else {
                    SecurityContextHolder.getContext().setAuthentication(buildAuthentication(claims, request));
                }
            } catch (ExpiredJwtException e) {
                reject(request, "Access-токен просрочен — обновите пару токенов через /api/auth/refresh");
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("JWT не принят: {}", e.getMessage());
                reject(request, "Access-токен недействителен");
            }
        }

        filterChain.doFilter(request, response);
    }

    private Long userIdOf(Claims claims) {
        Number uid = claims.get(JwtTokenProvider.CLAIM_UID, Number.class);
        return uid == null ? null : uid.longValue();
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(Claims claims, HttpServletRequest request) {
        List<String> roles = tokenProvider.roles(claims);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        UserPrincipal principal = new UserPrincipal(
                claims.get(JwtTokenProvider.CLAIM_UID, Number.class).longValue(),
                claims.getSubject(),
                null,
                roles.contains(Roles.ADMIN));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authentication;
    }

    private void reject(HttpServletRequest request, String message) {
        SecurityContextHolder.clearContext();
        request.setAttribute(RestAuthenticationEntryPoint.ATTR_JWT_ERROR, message);
    }

    private void rejectForbidden(HttpServletRequest request, String message) {
        SecurityContextHolder.clearContext();
        request.setAttribute(RestAuthenticationEntryPoint.ATTR_FORBIDDEN_MESSAGE, message);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            String value = header.substring(BEARER.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
