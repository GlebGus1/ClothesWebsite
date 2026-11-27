package com.example.appShop.config;

import com.example.appShop.security.JwtAuthenticationFilter;
import com.example.appShop.security.RateLimitFilter;
import com.example.appShop.security.RestAccessDeniedHandler;
import com.example.appShop.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT-безопасность (Spring Security 6, лямбда-синтаксис).
 *
 * Что пропало по сравнению со старым конфигом:
 *  - .formLogin() и страница /login (вход теперь делает POST /api/auth/login);
 *  - сессии и кука JSESSIONID (sessionCreationPolicy = STATELESS);
 *  - CSRF (не нужен: у нас нет авто-отправляемых куки, токен передаёт заголовок);
 *  - ViewController и Thymeleaf-шаблоны.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity           // включает @PreAuthorize, если понадобится «тонкая» проверка прав
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final AuthenticationManager authenticationManager;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          AuthenticationManager authenticationManager,
                          CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.authenticationManager = authenticationManager;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)   // 401 без токена
                        .accessDeniedHandler(accessDeniedHandler))            // 403 не той роли
                .authorizeHttpRequests(requests -> requests
                        // предзапрос браузера (preflight) обязан быть доступен без токена
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/verify-email",
                                "/api/auth/verification-code", "/api/auth/captcha-status").permitAll()
                        // события воронки продаж шлют и гости (только с согласия на cookies,
                        // которое проверяет фронтенд; сервер тут просто не пускает спам по лимиту)
                        .requestMatchers("/api/track/**").permitAll()
                        // активные коллекции (баннер и чипсы на витрине) видят и гости
                        .requestMatchers(HttpMethod.GET, "/api/collections").permitAll()
                        // каталог и картинки смотрят и гости
                        .requestMatchers(HttpMethod.GET, "/api/tshirts", "/api/tshirts/*", "/api/images/*")
                        .permitAll()
                        // документация API
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // внутренняя страница ошибок сервлет-контейнера: forward на /error
                        // не должен превращать честный 404 в 401
                        .requestMatchers("/error").permitAll()
                        // всё остальное — только ADMIN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/tshirts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/tshirts/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/tshirts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/tshirts/*", "/api/images/*").hasRole("ADMIN")
                        // избранное и промокоды — только вошедшим пользователям
                        // (anyRequest().authenticated() закрыл бы их и сам, но явное правило
                        // документирует намерение и не даст ошибиться при будущих правках)
                        .requestMatchers("/api/favorites/**", "/api/promo-codes/**").authenticated()
                        // /api/auth/me, /api/orders (создание и «мои») — любой вошедший
                        .anyRequest().authenticated())
                .authenticationManager(authenticationManager)
                // Фильтр лимитов обязан стоять САМЫМ ранним: отказ по лимиту должен
                // случиться до разбора JWT, до обращения к БД и до дорогого BCrypt.
                // Якорь — LogoutFilter: это самый первый «известный» фильтр Spring Security,
                // поэтому вставка «перед ним» даёт нам начало цепочки.
                // (На JwtAuthenticationFilter как якорь ссылаться нельзя: это наш собственный
                // класс, а Spring Security умеет вставлять только относительно фильтров,
                // чей порядок ему известен — иначе старт приложения падает с
                // "does not have a registered order".)
                .addFilterBefore(rateLimitFilter, LogoutFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.originsOrDefault());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-Visitor-Id — обезличенный ключ посетителя для статистики просмотров:
        // фронтенд шлёт его только после согласия в cookie-баннере. Список заголовков
        // должен включать его явно, иначе браузер не пропустит запрос (CORS).
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Visitor-Id"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setMaxAge(3600L);
        // куки/учётные данные не используем: авторизация только через заголовок Authorization
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
