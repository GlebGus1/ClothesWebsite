package com.example.appShop.services;

import com.example.appShop.entities.User;
import com.example.appShop.repositories.UserRepository;
import com.example.appShop.security.UserPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Мост между нашей таблицей users и Spring Security.
 *
 * Используется ТОЛЬКО при логине (и регистрации админом): DaoAuthenticationProvider
 * вызывает loadUserByUsername, чтобы сравнить BCrypt-хэш пароля.
 * На все остальные запросы пользователя «собирает» из JWT JwtAuthenticationFilter —
 * поэтому база при обычном запросе не трогается (stateless).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findUserByEmail(email);
        if (user == null) {
            // Формулировка та же, что и при неверном пароле: нельзя «подсказывать»,
            // существует ли такой e-mail.
            throw new UsernameNotFoundException("Неверный e-mail или пароль");
        }
        return UserPrincipal.fromEntity(user);
    }
}
