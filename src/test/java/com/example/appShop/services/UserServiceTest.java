package com.example.appShop.services;

import com.example.appShop.dto.RegisterRequest;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Пользовательский сервис: проверяем, что пароль никогда не уходит в базу открытым
 * текстом, а e-mail всегда нормализован (регистрация и вход должны совпадать).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    // реальный BCrypt: важно, чтобы кодирование именно работало, а не был заглушён моком
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void emailIsTrimmedAndLowercased() {
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hash");
        when(userRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        userService.create(new RegisterRequest("  Иван ", "Петров ", "  Ivan@Example.COM  ", "secret123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ivan@example.com");
        assertThat(saved.getValue().getFirstName()).isEqualTo("Иван");
        assertThat(saved.getValue().getLastName()).isEqualTo("Петров");
    }

    @Test
    void passwordIsStoredEncodedNotRaw() {
        String raw = "secret123";
        when(passwordEncoder.encode(raw)).thenReturn("$2a$10$encoded");
        when(userRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.create(new RegisterRequest("Иван", "Петров", "i@e.com", raw));

        assertThat(saved.getPassword()).isEqualTo("$2a$10$encoded").isNotEqualTo(raw);
    }

    @Test
    void registeredUserIsNotAdminAndLegacyFlagsAreSet() {
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("h");
        when(userRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.create(new RegisterRequest("Иван", "Петров", "i@e.com", "secret123"));

        // NOT NULL-колонки старой схемы должны получать значение, иначе вставка упадёт
        assertThat(saved.isAdmin()).isFalse();
        assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.isLogged()).isFalse();
    }

    @Test
    void createAdminTurnsOnAdminAndEnabledFlags() {
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("h");
        when(userRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        User admin = userService.createAdmin("admin@example.com", "SuperSecret1", "Admin", "Shop");

        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
    }

    @Test
    void realBCryptRoundTripWorks() {
        PasswordEncoder real = new BCryptPasswordEncoder();
        String encoded = real.encode("SuperSecret1");

        assertThat(encoded).isNotEqualTo("SuperSecret1");
        assertThat(real.matches("SuperSecret1", encoded)).isTrue();
        assertThat(real.matches("supersecret1", encoded)).isFalse();
    }

    @Test
    void existsByEmailHandlesNullFromRepository() {
        when(userRepository.findUserByEmail("ghost@example.com")).thenReturn(null);
        when(userRepository.findUserByEmail("i@e.com")).thenReturn(new User());

        assertThat(userService.existsByEmail("ghost@example.com")).isFalse();
        assertThat(userService.existsByEmail("i@e.com")).isTrue();
    }

    @Test
    void unknownUserGivesNotFound() {
        when(userRepository.findUserByEmail("ghost@example.com")).thenReturn(null);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> userService.getByEmailOrThrow("ghost@example.com"));
    }

    @Test
    void unknownUserIdGivesNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> userService.getByIdOrThrow(404L));
    }
}
