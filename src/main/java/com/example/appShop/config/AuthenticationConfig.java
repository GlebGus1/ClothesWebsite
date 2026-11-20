package com.example.appShop.config;

import com.example.appShop.services.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * «Кто такой пользователь и как мы проверяем его пароль».
 *
 * Отдельно от SecurityConfig — так в SecurityConfig остаётся только цепочка фильтров,
 * а правила аутентификации живут здесь. Раньше этот же код был внутри SecurityConfig
 * через AuthenticationManagerBuilder (в Spring Security 6 он устарел).
 */
@Configuration
public class AuthenticationConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: соль внутри хэша + намеренная медленность (устойчивость к перебору)
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(CustomUserDetailsService userDetailsService,
                                                              PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // true (по умолчанию): «пользователь не найден» и «неверный пароль» дают одинаковую ошибку
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider) {
        return new ProviderManager(List.of(daoAuthenticationProvider));
    }
}
