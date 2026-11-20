package com.example.appShop.services;

import com.example.appShop.config.JwtProperties;
import com.example.appShop.dto.AuthRequest;
import com.example.appShop.dto.AuthResponse;
import com.example.appShop.dto.RefreshRequest;
import com.example.appShop.dto.RegisterRequest;
import com.example.appShop.dto.TokenPair;
import com.example.appShop.entities.RefreshToken;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.ConflictException;
import com.example.appShop.exceptions.InvalidCredentialsException;
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
 * Оркестратор авторизации. Проверяем самое дорогое: правила ротации,
 * нормализацию e-mail и то, что мы не подсказываем злоумышленнику лишнего.
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

    private JwtTokenProvider tokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(new JwtProperties(
                "auth-service-test-secret-auth-service-test-secret-0123456789-abcd",
                Duration.ofMinutes(15), Duration.ofDays(7), "appshop"));
        authService = new AuthService(authenticationManager, userService, userDetailsService,
                refreshTokenService, tokenProvider);
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
    void registerReturnsBothTokensAndProfile() {
        when(userService.existsByEmail("user@example.com")).thenReturn(false);
        when(userService.create(any())).thenReturn(user(5L, "user@example.com", false));
        when(refreshTokenService.issue(any())).thenReturn(new TokenPair("access-1", "refresh-1"));

        AuthResponse response = authService.register(new RegisterRequest("Иван", "Петров",
                " User@Example.com ", "secret123"));

        assertThat(response.accessToken()).isEqualTo("access-1");
        assertThat(response.refreshToken()).isEqualTo("refresh-1");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(15 * 60);
        assertThat(response.user().email()).isEqualTo("user@example.com");
        assertThat(response.user().admin()).isFalse();
    }

    @Test
    void registerIssuesTokensForFreshlySavedUser() {
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(userService.create(any())).thenReturn(user(9L, "new@example.com", false));
        when(refreshTokenService.issue(any())).thenReturn(new TokenPair("a", "r"));

        authService.register(new RegisterRequest("Нов", "Поль", "new@example.com", "secret123"));

        ArgumentCaptor<UserPrincipal> principal = ArgumentCaptor.forClass(UserPrincipal.class);
        verify(refreshTokenService).issue(principal.capture());
        // id берётся из сохранённой сущности, а не из запроса клиента
        assertThat(principal.getValue().getId()).isEqualTo(9L);
        assertThat(principal.getValue().getUsername()).isEqualTo("new@example.com");
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
