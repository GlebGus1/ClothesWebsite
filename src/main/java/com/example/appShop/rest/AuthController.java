package com.example.appShop.rest;

import com.example.appShop.dto.AuthRequest;
import com.example.appShop.dto.AuthResponse;
import com.example.appShop.dto.RefreshRequest;
import com.example.appShop.dto.RegisterRequest;
import com.example.appShop.dto.SendCodeRequest;
import com.example.appShop.dto.UserDto;
import com.example.appShop.dto.VerificationStatusDto;
import com.example.appShop.dto.VerifyEmailRequest;
import com.example.appShop.mapper.UserMapper;
import com.example.appShop.security.UserPrincipal;
import com.example.appShop.services.AuthService;
import com.example.appShop.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Всё, что связано с входом. Публичные ручки: register / verify-email /
 * verification-code / login / refresh.
 * Приватные: me / logout (нужен валидный access-токен).
 *
 * Нигде не создаётся сессия и не ставится кука — ответ всегда JSON с двумя токенами
 * (кроме register/verification-code: там токенов нет, пока e-mail не подтверждён).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /**
     * Регистрация создаёт неподтверждённый аккаунт и отправляет PIN-код.
     * Токенов в ответе нет — их выдаст /api/auth/verify-email.
     */
    @PostMapping("/register")
    public ResponseEntity<VerificationStatusDto> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** Ввод PIN-кода из письма. Успех = аккаунт активирован + сразу выдаются токены. */
    @PostMapping("/verify-email")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    /** «Код не пришёл» — генерируем и отправляем новый PIN-код. */
    @PostMapping("/verification-code")
    public VerificationStatusDto resendCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.resendVerificationCode(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request);
    }

    /** Фронтенд вызывает этот метод автоматически, когда access-токен истёк (401). */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    /** Отзываем refresh-токен в БД. Access-токен «убить» нельзя — он просто доживёт свои 15 минут. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return UserMapper.toDto(userService.getByEmailOrThrow(principal.getUsername()));
    }
}
