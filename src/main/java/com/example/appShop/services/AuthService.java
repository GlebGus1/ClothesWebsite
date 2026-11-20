package com.example.appShop.services;

import com.example.appShop.dto.AuthRequest;
import com.example.appShop.dto.AuthResponse;
import com.example.appShop.dto.RefreshRequest;
import com.example.appShop.dto.RegisterRequest;
import com.example.appShop.dto.TokenPair;
import com.example.appShop.entities.RefreshToken;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.ConflictException;
import com.example.appShop.exceptions.InvalidCredentialsException;
import com.example.appShop.mapper.UserMapper;
import com.example.appShop.security.JwtTokenProvider;
import com.example.appShop.security.RefreshTokenService;
import com.example.appShop.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Оркестратор авторизации: register / login / refresh / logout / me.
 *
 * Логика такая:
 *  - проверку пароля отдаём AuthenticationManager'у (он вызовет CustomUserDetailsService + BCrypt);
 *  - сами токены выдаёт RefreshTokenService (он же пишет хэш refresh-токена в БД);
 *  - при refresh обязательно читаем пользователя из БД, чтобы роли (USER/ADMIN) брались актуальные.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider tokenProvider;

    public AuthService(AuthenticationManager authenticationManager,
                       UserService userService,
                       UserDetailsService userDetailsService,
                       RefreshTokenService refreshTokenService,
                       JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.refreshTokenService = refreshTokenService;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userService.existsByEmail(email)) {
            throw new ConflictException("Такой e-mail уже зарегистрирован");
        }
        User created = userService.create(request);
        log.info("Зарегистрирован пользователь id={}", created.getId());
        return issueTokens(UserPrincipal.fromEntity(created), created);
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        UserPrincipal principal = authenticate(request.email().trim().toLowerCase(), request.password());
        User user = userService.getByEmailOrThrow(principal.getUsername());
        return issueTokens(principal, user);
    }

    /**
     * Ротация refresh-токена: старый одноразовый. Если кто-то украдёт refresh-токен и
     * попробует использовать его повторно, он уже отозван -> 401, и клиент пойдёт логиниться заново.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenService.validateAndGetEntity(request.refreshToken());
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(stored.getUserEmail());
        User user = userService.getByEmailOrThrow(principal.getUsername());

        TokenPair pair = refreshTokenService.issue(principal);
        refreshTokenService.markRotated(stored, pair.refreshToken());

        return new AuthResponse(pair.accessToken(), pair.refreshToken(), AuthResponse.TYPE,
                tokenProvider.accessExpiresInSeconds(), UserMapper.toDto(user));
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private UserPrincipal authenticate(String email, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            return (UserPrincipal) authentication.getPrincipal();
        } catch (AuthenticationException e) {
            // Не говорим клиенту, чего именно не хватило: e-mail существует или пароль.
            throw new InvalidCredentialsException();
        }
    }

    private AuthResponse issueTokens(UserPrincipal principal, User user) {
        TokenPair pair = refreshTokenService.issue(principal);
        return new AuthResponse(pair.accessToken(), pair.refreshToken(), AuthResponse.TYPE,
                tokenProvider.accessExpiresInSeconds(), UserMapper.toDto(user));
    }
}
