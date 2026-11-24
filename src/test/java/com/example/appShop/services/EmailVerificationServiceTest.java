package com.example.appShop.services;

import com.example.appShop.config.VerificationProperties;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PIN-код подтверждения e-mail. Проверяем три вещи, за которые в реальном магазине
 * бьют сильнее всего: код действительно из 6 случайных цифр, просроченный код не
 * принимается, и активация невозможна без совпадения кода.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailSender emailSender;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, emailSender,
                new VerificationProperties(true, Duration.ofHours(24)));
    }

    private static User notConfirmed(String email) {
        User user = new User();
        user.setId(1L);
        user.setFirstName("Иван");
        user.setLastName("Петров");
        user.setEmail(email);
        user.setEnabled(false);
        return user;
    }

    @Test
    void issueCodeSavesSixDigitsAndDeactivatesAccount() {
        User user = notConfirmed("new@example.com");
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(true);

        String code = service.issueCode(user);

        assertThat(code).matches("\\d{6}");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getVerificationCode()).isEqualTo(code);
        assertThat(saved.getValue().isEnabled()).isFalse();
        assertThat(saved.getValue().getVerificationCodeSentAt()).isNotNull();
    }

    @Test
    void codeGoesToLogWhenSmtpIsNotConfigured() {
        User user = notConfirmed("new@example.com");
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(false);

        String code = service.issueCode(user);

        // письмо не ушло, но код сохранён и напечатан в логе — регистрация не падает
        verify(userRepository).save(user);
        assertThat(user.getVerificationCode()).isEqualTo(code);
        verify(emailSender).send(eq("new@example.com"), anyString(), contains(code));
    }

    @Test
    void confirmActivatesAccountAndErasesCode() {
        User user = notConfirmed("new@example.com");
        user.setVerificationCode("123456");
        user.setVerificationCodeSentAt(Instant.now());

        service.confirm(user, "123 456");

        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getVerificationCode()).isNull();
        assertThat(user.getVerificationCodeSentAt()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void confirmRefusesWrongCode() {
        User user = notConfirmed("new@example.com");
        user.setVerificationCode("123456");
        user.setVerificationCodeSentAt(Instant.now());

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.confirm(user, "000000"))
                .withMessage("Неверный PIN-код");
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void confirmRefusesExpiredCode() {
        User user = notConfirmed("new@example.com");
        user.setVerificationCode("123456");
        user.setVerificationCodeSentAt(Instant.now().minus(25, ChronoUnit.HOURS));

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.confirm(user, "123456"))
                .withMessageContaining("просрочен");
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void confirmDoesNothingForAlreadyActiveAccount() {
        User user = notConfirmed("user@example.com");
        user.setEnabled(true);

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.confirm(user, "123456"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void activateIsUsedWhenVerificationIsDisabled() {
        User user = notConfirmed("shop@example.com");
        user.setVerificationCode("123456");

        service.activate(user);

        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getVerificationCode()).isNull();
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }
}
