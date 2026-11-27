package com.example.appShop.security;

import com.example.appShop.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Фильтр-«шлюз»: превращает превышение лимита в HTTP 429 + заголовок Retry-After
 * и решает, какие запросы лимитировать вообще нельзя.
 *
 * Тест без БД и без сервера: MockHttpServletRequest/Response + MockFilterChain.
 */
class RateLimitFilterTest {

    /**
     * В приложении в фильтр попадает ObjectMapper от Spring Boot (с модулем JavaTime),
     * поэтому и в тесте регистрируем тот же модуль: иначе ошибка «429» не сериализуется
     * и тест падает не по теме проверки.
     */
    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static RateLimitFilter filter(RateLimitProperties properties) {
        InMemoryIpRateLimiter limiter = new InMemoryIpRateLimiter(properties);
        return new RateLimitFilter(limiter, new ClientIpResolver(properties), properties, objectMapper());
    }

    private static MockHttpServletRequest request(String method, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void sixthLoginInMinuteGetsTooManyRequests() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 80, 5, 1, 10, false));

        MockHttpServletResponse last = null;
        for (int attempt = 1; attempt <= 6; attempt++) {
            last = new MockHttpServletResponse();
            filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"), last, new MockFilterChain());
        }

        assertThat(last.getStatus()).isEqualTo(429);
        assertThat(last.getHeader("Retry-After")).isNotNull().containsOnlyDigits();
        assertThat(last.getContentAsString()).contains("Слишком много запросов");
    }

    @Test
    void blockedClientIsNotPassedFurtherIntoApplication() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 80, 1, 1, 10, false));
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"),
                new MockHttpServletResponse(), new MockFilterChain());

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"), response, chain);

        // главный смысл защиты: запрос не дошёл ни до BCrypt, ни до БД
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void limitsAreSeparatedForLoginAndRegistration() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 80, 1, 1, 10, false));
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"),
                new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse registerResponse = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/auth/register", "203.0.113.7"),
                registerResponse, new MockFilterChain());

        // неудачные попытки входа не должны закрывать возможность зарегистрироваться
        assertThat(registerResponse.getStatus()).isNotEqualTo(429);
    }

    @Test
    void productImagesDoNotCountTowardsApiLimit() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 1, 5, 1, 10, false));

        filter.doFilter(request("GET", "/api/tshirts", "203.0.113.7"),
                new MockHttpServletResponse(), new MockFilterChain());

        for (int image = 0; image < 40; image++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", "/api/images/" + image, "203.0.113.7"), response, new MockFilterChain());
            // страница каталога тянет десятки картинок: лимит не должен ломать обычную загрузку
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void corsPreflightAndStaticAssetsAreNotLimited() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 1, 1, 1, 10, false));

        MockHttpServletResponse preflight = new MockHttpServletResponse();
        filter.doFilter(request("OPTIONS", "/api/tshirts", "203.0.113.7"), preflight, new MockFilterChain());

        MockHttpServletResponse spa = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/assets/app.js", "203.0.113.7"), spa, new MockFilterChain());

        assertThat(preflight.getStatus()).isNotEqualTo(429);
        assertThat(spa.getStatus()).isNotEqualTo(429);
    }

    @Test
    void spoofedForwardedForIsIgnoredUnlessProxyIsTrusted() throws Exception {
        // trustProxyHeaders=false: злоумышленник не должен обходить лимит, дописав свой IP в заголовок
        RateLimitFilter filter = filter(new RateLimitProperties(true, 80, 1, 1, 10, false));
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"),
                new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest spoofed = request("POST", "/api/auth/login", "203.0.113.7");
        spoofed.addHeader("X-Forwarded-For", "91.98.76.54");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(spoofed, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void forwardedForIsUsedWhenProxyIsTrusted() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(true, 80, 1, 1, 10, true));
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"),
                new MockHttpServletResponse(), new MockFilterChain());

        // за прокси реальный посетитель — в X-Forwarded-For, и его надо различать
        MockHttpServletRequest visitor = request("POST", "/api/auth/login", "10.0.0.5");
        visitor.addHeader("X-Forwarded-For", "91.98.76.54");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(visitor, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void disabledFilterLetsEverythingPass() throws Exception {
        RateLimitFilter filter = filter(new RateLimitProperties(false, 1, 1, 1, 10, false));

        for (int attempt = 0; attempt < 30; attempt++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"), response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }
}
