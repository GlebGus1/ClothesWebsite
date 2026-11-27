package com.example.appShop.security;

import com.example.appShop.config.RateLimitProperties;
import com.example.appShop.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ограничение частоты запросов по IP: 429 Too Many Requests + заголовок Retry-After.
 *
 * Фильтр стоит РАНО — до Spring Security и до контроллеров, то есть до обращения
 * к БД и до дорогого BCrypt. Смысл ровно в этом: дешёвый отказ должен случиться
 * до дорогой работы.
 *
 * Разные корзины (см. InMemoryIpRateLimiter.check):
 *  - /api/auth/login    -> authRequestsPerMinute (по умолчанию 5/мин на IP);
 *  - /api/auth/register -> столько же, но отдельная корзина;
 *  - остальные /api/**  -> publicRequestsPerMinute (по умолчанию 80/мин на IP).
 *
 * Что НЕ лимитируем: картинки товара (их тянет <img> на каждой карточке),
 * статику SPA, /error и CORS-preflight (OPTIONS) — иначе ломалась бы обычная
 * загрузка страницы, а не только атаки.
 *
 * Зарегистрирован вручную (config/RateLimitConfig) и поставлен в цепочку Spring Security
 * перед JwtAuthenticationFilter. Аннотации @Component на нём нет намеренно, а в
 * RateLimitConfig его «контейнерная» регистрация отключена: иначе фильтр отрабатывал бы
 * дважды за запрос (один раз как обычный servlet-фильтр и ещё раз внутри Security).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/auth/login";
    static final String REGISTER_PATH = "/api/auth/register";

    private final InMemoryIpRateLimiter rateLimiter;
    private final ClientIpResolver ipResolver;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(InMemoryIpRateLimiter rateLimiter,
                           ClientIpResolver ipResolver,
                           RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.ipResolver = ipResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isExempt(request.getMethod(), path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = ipResolver.resolve(request);
        InMemoryIpRateLimiter.Decision decision = decide(path, clientKey);

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeTooManyRequests(request, response, decision);
    }

    private InMemoryIpRateLimiter.Decision decide(String path, String clientKey) {
        if (REGISTER_PATH.equals(path)) {
            return rateLimiter.checkRegistration(clientKey);
        }
        if (LOGIN_PATH.equals(path)) {
            return rateLimiter.checkAuthEndpoint(clientKey);
        }
        return rateLimiter.checkPublicApi(clientKey);
    }

    private boolean isExempt(String method, String path) {
        if (!properties.enabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true; // preflight не должен «съедать» лимит
        }
        if (path == null || !path.startsWith("/api/")) {
            return true; // статика SPA, /error, favicon
        }
        return path.startsWith("/api/images"); // картинки: их рисует браузер пачками
    }

    private void writeTooManyRequests(HttpServletRequest request,
                                      HttpServletResponse response,
                                      InMemoryIpRateLimiter.Decision decision) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String message = "Слишком много запросов с этого адреса. "
                + "Повторите попытку через " + decision.retryAfterSeconds() + " сек.";
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(HttpStatus.TOO_MANY_REQUESTS, message, request.getRequestURI()));
    }
}
