package com.example.appShop.services;

import com.example.appShop.config.JwtProperties;
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
import com.example.appShop.exceptions.AccountNotVerifiedException;
import com.example.appShop.exceptions.ConflictException;
import com.example.appShop.exceptions.InvalidCredentialsException;
import com.example.appShop.exceptions.InvalidTokenException;
import com.example.appShop.security.JwtTokenProvider;
import com.example.appShop.security.RefreshTokenService;
import com.example.appShop.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Оркестратор авторизации. Проверяем самое дорогое: правила ротации refresh-токена,
 * нормализацию e-mail, то, что мы не подсказываем злоумышленнику лишнего, и то,
 * что до подтверждения e-mail токенов у клиента нет.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserService userService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private EmailVerificationService verificationService;

    private JwtTokenProvider tokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(new JwtProperties(
                "auth-service-test-secret-auth-service-test-secret-0123456789-abcd",
                Duration.ofMinutes(15), Duration.ofDays(7), "appshop"));
        authService = new AuthService(authenticationManager, userService, userDetailsService,
                refreshTokenService, tokenProvider, verificationService);
    }

    @Test
    void registerRefusesExistingEmail() {
        when(userService.existsByEmail("user@example.com")).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> authService.register(new RegisterRequest("Иван", "Петров",
                        " User@example.com ", "secret123")));
        verify(userService, never()).create(any());
    }

    @Test
    void registerDoesNotIssueTokensUntilEmailIsConfirmed() {
        User notConfirmed = user(5L, "user@example.com", false);
        notConfirmed.setEnabled(false);
        when(userService.existsByEmail("user@example.com")).thenReturn(false);
        when(userService.create(any())).thenReturn(notConfirmed);
        when(verificationService.isVerificationRequired()).thenReturn(true);

        VerificationStatusDto status = authService.register(new RegisterRequest("Иван", "Петров",
                " User@Example.com ", "secret123"));

        assertThat(status.requiresVerification()).isTrue();
        assertThat(status.email()).isEqualTo("user@example.com");
        assertThat(status.message()).contains("PIN");
        // главное: регистрация не выдаёт ключи от магазина
        verify(refreshTokenService, never()).issue(any());
        verify(verificationService).issueCode(notConfirmed);
    }

    @Test
    void registerActivatesAccountWhenVerificationIsDisabled() {
        User fresh = user(6L, "shop@example.com", false);
        when(userService.existsByEmail("shop@example.com")).thenReturn(false);
        when(userService.create(any())).thenReturn(fresh);
        when(verificationService.isVerificationRequired()).thenReturn(false);

        VerificationStatusDto status = authService.register(new RegisterRequest("Ива", "Петров",
                " Shop@example.com ", "secret123"));

        assertThat(status.requiresVerification()).isFalse();
        verify(verificationService).activate(fresh);
        verify(verificationService, never()).issueCode(any());
    }

    @Test
    void verifyEmailIssuesTokensOnlyAfterCodeIsConfirmed() {
        User notConfirmed = user(7L, "new@example.com", false);
        notConfirmed.setEnabled(false);
        when(userService.getByEmailOrThrow("new@example.com")).thenReturn(notConfirmed);
        when(refreshTokenService.issue(any())).thenReturn(new TokenPair("access-1", "refresh-1"));

        AuthResponse response = authService.verifyEmail(new VerifyEmailRequest(" new@example.com ", "421317"));

        verify(verificationService).confirm(notConfirmed, "421317");
        assertThat(response.accessToken()).isEqualTo("access-1");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(15 * 60);
        assertThat(response.user().email()).isEqualTo("new@example.com");

        ArgumentCaptor<UserPrincipal> principal = ArgumentCaptor.forClass(UserPrincipal.class);
        verify(refreshTokenService).issue(principal.capture());
        // id берём из сохранённой сущности, а не из запроса клиента
        assertThat(principal.getValue().getId()).isEqualTo(7L);
    }

    @Test
    void resendCodeDoesNotRevealWhetherEmailExists() {
        when(userService.findByEmail("ghost@example.com")).thenReturn(java.util.Optional.empty());

        VerificationStatusDto status = authService.resendVerificationCode(new SendCodeRequest("ghost@example.com"));

        assertThat(status.requiresVerification()).isTrue();
        verify(verificationService, never()).issueCode(any());
    }

    @Test
    void resendSkipsSendingForAlreadyConfirmedEmail() {
        User confirmed = user(3L, "user@example.com", false);
        confirmed.setEnabled(true);
        when(userService.findByEmail("user@example.com")).thenReturn(java.util.Optional.of(confirmed));

        VerificationStatusDto status = authService.resendVerificationCode(new SendCodeRequest("user@example.com"));

        assertThat(status.requiresVerification()).isFalse();
        verify(verificationService, never()).issueCode(any());
    }

    @Test
    void loginNormalizesEmailBeforeChecking() {
        UserPrincipal principal = new UserPrincipal(1L, "user@example.com", "hash", false);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(userService.getByEmailOrThrow("user@example.com")).thenReturn(user(1L, "user@example.com", false));
        when(refreshTokenService.issue(any())).thenReturn(new TokenPair("access-2", "refresh-2"));

        AuthResponse response = authService.login(new AuthRequest("  User@Example.com ", "secret123"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> request =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(request.capture());
        assertThat(request.getValue().getPrincipal()).isEqualTo("user@example.com");
        assertThat(response.accessToken()).isEqualTo("access-2");
    }

    @Test
    void loginDoesNotRevealWhetherEmailExists() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService.login(new AuthRequest("ghost@example.com", "wrong")))
                .withMessage("Неверный e-mail или пароль");
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void loginOfUnconfirmedAccountAsksForPinCode() {
        // AuthenticationManager бросает DisabledException, когда UserDetails.isEnabled()==false:
        // пароль верный, но e-mail не подтверждён PIN-кодом
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThatExceptionOfType(AccountNotVerifiedException.class)
                .isThrownBy(() -> authService.login(new AuthRequest("new@example.com", "secret123")));
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void refreshReplacesOldTokenAndReturnsNewPair() {
        RefreshToken stored = new RefreshToken();
        stored.setUserEmail("user@example.com");
        UserPrincipal principal = new UserPrincipal(1L, "user@example.com", "hash", false);
        when(refreshTokenService.validateAndGetEntity("old-refresh")).thenReturn(stored);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(userService.getByEmailOrThrow("user@example.com")).thenReturn(user(1L, "user@example.com", false));
        when(refreshTokenService.issue(principal)).thenReturn(new TokenPair("access-new", "refresh-new"));

        AuthResponse response = authService.refresh(new RefreshRequest("old-refresh"));

        assertThat(response.refreshToken()).isEqualTo("refresh-new");
        verify(refreshTokenService).markRotated(stored, "refresh-new");
    }

    @Test
    void refreshReadsRolesFromDatabaseNotFromOldToken() {
        RefreshToken stored = new RefreshToken();
        stored.setUserEmail("boss@example.com");
        UserPrincipal admin = new UserPrincipal(2L, "boss@example.com", "hash", true);
        when(refreshTokenService.validateAndGetEntity("r")).thenReturn(stored);
        when(userDetailsService.loadUserByUsername("boss@example.com")).thenReturn(admin);
        when(userService.getByEmailOrThrow("boss@example.com")).thenReturn(user(2L, "boss@example.com", true));
        when(refreshTokenService.issue(any())).thenReturn(new TokenPair("a", "b"));

        AuthResponse response = authService.refresh(new RefreshRequest("r"));

        // повышение прав видно сразу, старый access-токен доживает максимум 15 минут
        assertThat(response.user().admin()).isTrue();
    }

    @Test
    void refreshDoesNotExtendAccessForUnconfirmedAccount() {
        RefreshToken stored = new RefreshToken();
        stored.setUserEmail("new@example.com");
        UserPrincipal unconfirmed = new UserPrincipal(3L, "new@example.com", "hash", false, false);
        when(refreshTokenService.validateAndGetEntity("r")).thenReturn(stored);
        when(userDetailsService.loadUserByUsername("new@example.com")).thenReturn(unconfirmed);
        when(userService.getByEmailOrThrow("new@example.com")).thenReturn(user(3L, "new@example.com", false));

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> authService.refresh(new RefreshRequest("r")));

        // токен, которым «пробуют» войти в неподтверждённый аккаунт, отзываем
        verify(refreshTokenService).revoke("r");
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void logoutRevokesRefreshToken() {
        authService.logout(new RefreshRequest("raw-refresh"));

        verify(refreshTokenService).revoke("raw-refresh");
    }

    private User user(long id, String email, boolean admin) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName("Иван");
        user.setLastName("Петров");
        user.setPassword("bcrypt-hash");
        user.setAdmin(admin);
        return user;
    }
}
