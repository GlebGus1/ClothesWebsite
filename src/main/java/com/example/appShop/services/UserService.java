package com.example.appShop.services;

import com.example.appShop.dto.RegisterRequest;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Работа с таблицей users. Пароль здесь ВСЕГДА кодируется BCrypt-ом,
 * поэтому даже случайный «сырой» create не сохранит пароль в открытом виде.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(userRepository.findUserByEmail(email));
    }

    @Transactional(readOnly = true)
    public User getByEmailOrThrow(String email) {
        return findByEmail(email).orElseThrow(() -> new NotFoundException("Пользователь", email));
    }

    @Transactional(readOnly = true)
    public User getByIdOrThrow(long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("Пользователь", id));
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.findUserByEmail(email) != null;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public User create(RegisterRequest request) {
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        // legacy-поля старой схемы: оставлены как есть, чтобы NOT NULL-колонки не ломали вставку
        user.setEnabled(false);
        user.setLogged(false);
        user.setAdmin(false);
        return userRepository.save(user);
    }

    /** Служебный метод для «создать админа при старте» (см. AdminBootstrapRunner). */
    @Transactional
    public User createAdmin(String email, String rawPassword, String firstName, String lastName) {
        User user = create(new RegisterRequest(firstName, lastName, email, rawPassword, null));
        user.setAdmin(true);
        user.setEnabled(true);
        return userRepository.save(user);
    }
}
