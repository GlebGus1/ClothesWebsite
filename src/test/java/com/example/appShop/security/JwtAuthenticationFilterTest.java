package com.example.appShop.security;

import com.example.appShop.config.JwtProperties;
import com.example.appShop.repositories.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Фильтр JWT: как заголовок превращается в аутентификацию Spring Security.
 * Тесты без БД и без сервера: участвуют только сам фильтр и реальный JwtTokenProvider.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "filter-test-secret-filter-test-secret-filter-test-secret-0123456789";

    private JwtTokenProvider tokenProvider;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofMinutes(15),
                Duration.ofDays(7), "appshop"));
        // Запросы «забанен ли» идут в базу, поэтому в тесте — заглушка репозитория:
        // по умолчанию Mockito отвечает false, то есть «аккаунт обычный, не забанен».
        userRepository = mock(UserRepository.class);
        filter = new JwtAuthenticationFilter(tokenProvider, new AccountStatusGuard(userRepository));
        principal = new UserPrincipal(11L, "shopper@example.com", "hash", true);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bearerAccessTokenAuthenticatesRequest() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokenProvider.createAccessToken(principal));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(((UserPrincipal) authentication.getPrincipal()).getId()).isEqualTo(11L);
        assertThat(authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .toList())
                .containsExactlyInAnyOrder(Roles.USER, Roles.ADMIN);
    }

    @Test
    void refreshTokenInHeaderIsRejected() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokenProvider.createRefreshToken(principal));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // своей ошибки фильтр не отдаёт: причину 401 сформулирует RestAuthenticationEntryPoint
        assertThat(request.getAttribute(RestAuthenticationEntryPoint.ATTR_JWT_ERROR)).isNotNull()
                .asString().contains("access-токен");
    }

    @Test
    void expiredTokenPointsToRefreshEndpoint() throws ServletException, java.io.IOException {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(new JwtProperties(SECRET,
                Duration.ofMillis(-1000), Duration.ofDays(7), "appshop"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + expiredProvider.createAccessToken(principal));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(RestAuthenticationEntryPoint.ATTR_JWT_ERROR))
                .asString().contains("/api/auth/refresh");
    }

    @Test
    void forgedSignatureIsIgnored() throws ServletException, java.io.IOException {
        SecretKey foreign = Keys.hmacShaKeyFor((SECRET + "-another-key-for-the-attacker-xxxx").getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .setSubject("admin@example.com")
                .claim(JwtTokenProvider.CLAIM_UID, 1L)
                .claim(JwtTokenProvider.CLAIM_ROLES, List.of(Roles.USER, Roles.ADMIN))
                .claim(JwtTokenProvider.CLAIM_TYPE, TokenType.ACCESS.value())
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plus(Duration.ofMinutes(5))))
                .signWith(foreign, SignatureAlgorithm.HS512)
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + forged);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void requestWithoutHeaderStaysAnonymousAndGoesFurther() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // гостя не блокируем сам фильтр — это делает SecurityFilterChain (401 или публичный URL)
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void basicHeaderIsNotMisreadAsBearer() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(RestAuthenticationEntryPoint.ATTR_JWT_ERROR)).isNull();
    }

    /**
     * Access-токен выпущен ДО бана (он живёт 15 минут и отменить его нельзя),
     * а пользователя уже внесли в чёрный список. Фильтр обязан не пускать дальше
     * и пометить запрос так, чтобы RestAuthenticationEntryPoint отдал 403,
     * а не обычное «нужен вход» 401.
     */
    @Test
    void bannedAccountGetsForbiddenMarkInsteadOfAuthentication() throws ServletException, java.io.IOException {
        when(userRepository.existsByIdAndBannedTrue(11L)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokenProvider.createAccessToken(principal));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(RestAuthenticationEntryPoint.ATTR_FORBIDDEN_MESSAGE))
                .isNotNull()
                .asString().contains("заблокирован");
    }

    @Test
    void guestRequestDoesNotHitDatabaseForBanCheck() throws ServletException, java.io.IOException {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        // нет токена — нет и лишнего запроса в users (иначе каталог «под ддос» бил бы по базе)
        verify(userRepository, never()).existsByIdAndBannedTrue(anyLong());
    }
}
