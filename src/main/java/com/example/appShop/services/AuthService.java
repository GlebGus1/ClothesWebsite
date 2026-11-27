package com.example.appShop.services;

import com.example.appShop.dto.AuthRequest;
import com.example.appShop.dto.AuthResponse;
import com.example.appShop.dto.RefreshRequest;
import com.example.appShop.dto.RegisterRequest;
import com.example.appShop.dto.SendCodeRequest;
import com.example.appShop.dto.TokenPair;
import com.example.appShop.dto.VerificationStatusDto;
import com.example.appShop.dto.VerifyEmailRequest;
import com.example.appShop.entities.RefreshToken;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.AccountBannedException;
import com.example.appShop.exceptions.AccountNotVerifiedException;
import com.example.appShop.exceptions.ConflictException;
import com.example.appShop.exceptions.InvalidCredentialsException;
import com.example.appShop.exceptions.InvalidTokenException;
import com.example.appShop.mapper.UserMapper;
import com.example.appShop.security.AccountStatusGuard;
import com.example.appShop.security.JwtTokenProvider;
import com.example.appShop.security.RefreshTokenService;
import com.example.appShop.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Оркестратор авторизации: register / verify-email / login / refresh / logout / me.
 *
 * Логика такая:
 *  - проверку пароля отдаём AuthenticationManager'у (он вызовет CustomUserDetailsService + BCrypt);
 *  - сами токены выдаёт RefreshTokenService (он же пишет хэш refresh-токена в БД);
 *  - при refresh обязательно читаем пользователя из БД, чтобы роли (USER/ADMIN) брались актуальные.
 *
 * Порядок подтверждения e-mail:
 *  register -> пользователь enabled=false + PIN (письмо или лог) -> verify-email ->
 *  enabled=true -> выдаём токены. До подтверждения токенов нет, а логин возвращает 403.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String CODE_SENT_MESSAGE =
            "Мы отправили 6-значный PIN-код на почту. Введите его в форме подтверждения";
    private static final String NO_VERIFICATION_MESSAGE =
            "Подтверждение по e-mail отключено в настройках сервера — можно сразу войти";

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider tokenProvider;
    private final EmailVerificationService verificationService;
    private final AccountStatusGuard accountStatusGuard;
    private final RegistrationGuard registrationGuard;

    public AuthService(AuthenticationManager authenticationManager,
                       UserService userService,
                       UserDetailsService userDetailsService,
                       RefreshTokenService refreshTokenService,
                       JwtTokenProvider tokenProvider,
                       EmailVerificationService verificationService,
                       AccountStatusGuard accountStatusGuard,
                       RegistrationGuard registrationGuard) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.refreshTokenService = refreshTokenService;
        this.tokenProvider = tokenProvider;
        this.verificationService = verificationService;
        this.accountStatusGuard = accountStatusGuard;
        this.registrationGuard = registrationGuard;
    }

    /**
     * Регистрация НЕ выдаёт токены: аккаунт ещё не подтверждён. Возвращаем только
     * статус — фронтенд по нему уводит пользователя на форму ввода PIN-кода.
     *
     * captchaToken и clientIp нужны защитному шагу (RegistrationGuard): капча Turnstile
     * и суточный лимит регистраций с одного адреса. ПроверЯем ДО создания пользователя:
     * незачем записывать спам-строку в таблицу users и слать письмо.
     */
    @Transactional
    public VerificationStatusDto register(RegisterRequest request, String captchaToken, String clientIp) {
        registrationGuard.check(captchaToken, clientIp);

        String email = request.email().trim().toLowerCase();
        if (userService.existsByEmail(email)) {
            throw new ConflictException("Такой e-mail уже зарегистрирован");
        }

        User created = userService.create(request);

        if (!verificationService.isVerificationRequired()) {
            verificationService.activate(created);
            log.info("Зарегистрирован пользователь id={} (подтверждение e-mail отключено)", created.getId());
            return new VerificationStatusDto(created.getEmail(), false, NO_VERIFICATION_MESSAGE);
        }

        verificationService.issueCode(created);
        log.info("Зарегистрирован пользователь id={}, ожидает подтверждения e-mail", created.getId());
        return new VerificationStatusDto(created.getEmail(), true, CODE_SENT_MESSAGE);
    }

    /**
     * Подтверждение e-mail: сверяем PIN и только тогда выпускаем пару токенов.
     * Фронтенд получает AuthResponse и сразу показывает пользователя «вошедшим».
     */
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userService.getByEmailOrThrow(request.email().trim().toLowerCase());
        verificationService.confirm(user, request.code());
        return issueTokens(UserPrincipal.fromEntity(user), user);
    }

    /**
     * Повторная отправка кода (письмо не дошло / код просрочен). Отвечаем одинаково
     * для существующего и несуществующего e-mail, чтобы по ответу нельзя было
     * «прощупывать», есть ли аккаунт в базе.
     */
    @Transactional
    public VerificationStatusDto resendVerificationCode(SendCodeRequest request) {
        String email = request.email().trim().toLowerCase();
        return userService.findByEmail(email)
                .map(user -> resendFor(user))
                .orElseGet(() -> new VerificationStatusDto(email, true, CODE_SENT_MESSAGE));
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        UserPrincipal principal = authenticate(request.email().trim().toLowerCase(), request.password());
        User user = userService.getByEmailOrThrow(principal.getUsername());
        // Пароль правильный, но аккаунт в чёрном списке: вход запрещён (403),
        // токены не выдаём. Проверяем после проверки пароля, чтобы не подсказывать
        // существование аккаунта тем, кто ввёл неверный пароль.
        accountStatusGuard.assertNotBanned(user);
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

        if (!principal.isEnabled()) {
            // Аккаунт мог «съехать» в неподтверждённый (или админ отключил пользователя) —
            // новый токен выпускать нельзя, старый refresh отзываем.
            refreshTokenService.revoke(request.refreshToken());
            throw new InvalidTokenException("Аккаунт не подтверждён или отключён");
        }

        if (user.isBanned()) {
            // Забаненного не пускаем дальше и заодно чистим все его активные refresh-сессии:
            // новый access больше не выпустить, а старый доживёт максимум 15 минут.
            refreshTokenService.revokeAllForUser(user.getEmail());
            throw new AccountBannedException();
        }

        TokenPair pair = refreshTokenService.issue(principal);
        refreshTokenService.markRotated(stored, pair.refreshToken());

        return new AuthResponse(pair.accessToken(), pair.refreshToken(), AuthResponse.TYPE,
                tokenProvider.accessExpiresInSeconds(), UserMapper.toDto(user));
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private VerificationStatusDto resendFor(User user) {
        if (user.isEnabled()) {
            return new VerificationStatusDto(user.getEmail(), false,
                    "Этот e-mail уже подтверждён — можно войти в аккаунт");
        }
        verificationService.issueCode(user);
        return new VerificationStatusDto(user.getEmail(), true, CODE_SENT_MESSAGE);
    }

    private UserPrincipal authenticate(String email, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            return (UserPrincipal) authentication.getPrincipal();
        } catch (DisabledException e) {
            // Пароль правильный, но пользователь enabled=false (e-mail не подтверждён).
            throw new AccountNotVerifiedException();
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
