package com.example.appShop.services;

import com.example.appShop.entities.User;
import com.example.appShop.repositories.UserRepository;
import com.example.appShop.security.Roles;
import com.example.appShop.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

/**
 * Мост между таблицей users и Spring Security (используется только при логине).
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private static User user(boolean admin) {
        User user = new User();
        user.setId(4L);
        user.setEmail("user@example.com");
        user.setPassword("$2a$10$hash");
        user.setAdmin(admin);
        user.setFirstName("Иван");
        user.setLastName("Петров");
        return user;
    }

    @Test
    void loadsUserWithIdAndRoles() {
        when(userRepository.findUserByEmail("user@example.com")).thenReturn(user(false));

        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername("user@example.com");

        assertThat(principal.getId()).isEqualTo(4L);
        assertThat(principal.getUsername()).isEqualTo("user@example.com");
        assertThat(principal.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(principal.isAdmin()).isFalse();
        assertThat(principal.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .toList()).containsExactly(Roles.USER);
    }

    @Test
    void adminGetsAdminAndUserRole() {
        when(userRepository.findUserByEmail("boss@example.com")).thenReturn(user(true));
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername("boss@example.com");

        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.getAuthorities()).hasSize(2);
    }

    @Test
    void unknownUserGivesSameMessageAsWrongPassword() {
        when(userRepository.findUserByEmail("ghost@example.com")).thenReturn(null);

        // одинаковый текст не даёт подобрать существующие e-mail'ы
        assertThatExceptionOfType(UsernameNotFoundException.class)
                .isThrownBy(() -> userDetailsService.loadUserByUsername("ghost@example.com"))
                .withMessage("Неверный e-mail или пароль");
    }
}
