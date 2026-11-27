package com.example.appShop.config;

import com.example.appShop.security.ClientIpResolver;
import com.example.appShop.security.InMemoryIpRateLimiter;
import com.example.appShop.security.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Сборка фильтра ограничения частоты запросов.
 *
 * Почему бином, а не @Component на классе:
 *  1) нам нужно явно воткнуть фильтр в цепочку Spring Security (SecurityConfig#addFilterBefore),
 *     а не «положиться на порядок обхода контейнера»;
 *  2) Spring Boot любой найденный бин-фильтр автоматически навешивает ещё и на весь
 *     servlet-контейнер. Для нашего фильтра это лишний второй проход за тот же запрос,
 *     поэтому регистрацию контейнера ниже мы отключаем (setEnabled(false)).
 *     Путь у фильтра теперь ровно один — внутри SecurityFilterChain.
 *
 * Порядок внутри цепочки задаёт SecurityConfig: RateLimitFilter стоит ПЕРЕДО всеми
 * фильтрами безопасности, чтобы «лишний» трафик отсекался до разбора JWT и до обращений к БД.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(InMemoryIpRateLimiter rateLimiter,
                                           ClientIpResolver ipResolver,
                                           RateLimitProperties properties,
                                           ObjectMapper objectMapper) {
        return new RateLimitFilter(rateLimiter, ipResolver, properties, objectMapper);
    }

    /** Отключаем автоматическую регистрацию фильтра в servlet-контейнере (см. javadoc класса). */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
