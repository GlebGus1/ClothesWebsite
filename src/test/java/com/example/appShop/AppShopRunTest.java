package com.example.appShop;

import com.example.appShop.config.JwtProperties;
import com.example.appShop.repositories.RefreshTokenRepository;
import com.example.appShop.security.JwtAuthenticationFilter;
import com.example.appShop.security.JwtTokenProvider;
import com.example.appShop.security.RefreshTokenService;
import com.example.appShop.services.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционная проверка «сборки» приложения: контекст стартует, нужные бины на месте,
 * конфигурация JWT прочитана. Этот тест ловит поломки вида «два бина с одним именем»
 * и «не прочитался jwt.secret» — то, что юнит-тесты не видят.
 *
 * Требует запуска MySQL (профиль по умолчанию), БД appshop.
 */
@SpringBootTest
class AppShopRunTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void contextStarts() {
        assertThat(context.isActive()).isTrue();
    }

    @Test
    void securityWiringIsPresent() {
        // один бин на класс: дубликаты (например, старый SecurityConfig) уронили бы старт
        assertThat(context.getBeanNamesForType(JwtAuthenticationFilter.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(AuthService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(RefreshTokenService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(JwtTokenProvider.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(PasswordEncoder.class)).isNotEmpty();
    }

    @Test
    void jwtPropertiesAreReadFromConfig() {
        JwtProperties properties = context.getBean(JwtProperties.class);

        assertThat(properties.secret()).hasSizeGreaterThanOrEqualTo(64);
        assertThat(properties.issuerOrDefault()).isNotBlank();
        assertThat(properties.accessTokenTtlOrDefault()).isBetween(java.time.Duration.ofSeconds(60),
                java.time.Duration.ofHours(2));
    }

    @Test
    void refreshTokenTableIsReachable() {
        RefreshTokenRepository repository = context.getBean(RefreshTokenRepository.class);

        // не проверяем содержимое, только что таблица refresh_tokens существует и читается
        assertThat(repository.count()).isGreaterThanOrEqualTo(0);
    }
}
