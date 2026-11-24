package com.example.appShop.security;

import com.example.appShop.entities.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Наш "пользователь внутри Spring Security".
 *
 * В отличие от старого варианта (org.springframework.security.core.userdetails.User)
 * здесь есть ещё и id — он нужен, чтобы привязывать заказы к авторизованному пользователю,
 * не делая лишний запрос в БД.
 *
 * ВАЖНО: этот объект создаётся и из БД (логин), и из JWT (каждый запрос),
 * поэтому он не должен содержать ленивых связей и полей сессии.
 */
public class UserPrincipal implements UserDetails {

    private final long id;
    private final String email;
    private final String password;
    private final boolean admin;
    /** Аккаунт подтверждён по e-mail (enabled=true). Не подтверждённый не может войти. */
    private final boolean enabled;

    public UserPrincipal(long id, String email, String password, boolean admin) {
        this(id, email, password, admin, true);
    }

    public UserPrincipal(long id, String email, String password, boolean admin, boolean enabled) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.admin = admin;
        this.enabled = enabled;
    }

    public static UserPrincipal fromEntity(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPassword(),
                user.isAdmin(), user.isEnabled());
    }

    /** id пользователя — используется в заказах. */
    public long getId() {
        return id;
    }

    public boolean isAdmin() {
        return admin;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (admin) {
            return List.of(new SimpleGrantedAuthority(Roles.USER), new SimpleGrantedAuthority(Roles.ADMIN));
        }
        return List.of(new SimpleGrantedAuthority(Roles.USER));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    // Spring Security 6: эти методы больше не вызываются автоматически проверками аккаунта,
    // но UserDetails их требует. Проверку «подтверждён ли e-mail» включаем именно здесь:
    // DaoAuthenticationProvider вызывает isEnabled() сам (см. AccountStatusUserDetailsChecker),
    // и неподтверждённый аккаунт получает DisabledException ещё до выпуска токенов.
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
