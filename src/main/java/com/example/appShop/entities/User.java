package com.example.appShop.entities;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String password;
    @Column(name = "first_name", nullable = false, length = 20)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 20)
    private String lastName;
private boolean isLogged;
    private boolean isAdmin;
    private String verificationCode;

    /** Когда код был выдан: по этому времени считаем, не просрочен ли он (см. app.verification.code-ttl). */
    @Column(name = "verification_code_sent_at")
    private Instant verificationCodeSentAt;

    private boolean enabled;

    // Связь «кто сделал заказ» живёт в Order.user (@ManyToOne).
    // Дублирующее поле User.order из старого кода убрано (в БД колонка users.order_id
    // остаётся как есть — таблицы и колонки я не трогаю).

    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public boolean isLogged() {
        return isLogged;
    }

    public void setLogged(boolean logged) {
        isLogged = logged;
    }

    public User(String email) {
        this.email = email;
    }

    public User() {
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public Instant getVerificationCodeSentAt() {
        return verificationCodeSentAt;
    }

    public void setVerificationCodeSentAt(Instant verificationCodeSentAt) {
        this.verificationCodeSentAt = verificationCodeSentAt;
    }

    /**
     * Избранное («сердечко»): ManyToMany через таблицу-связку user_favorites
     * (user_id, tshirt_id). Связь односторонняя: TShirt не знает о пользователях,
     * иначе сериализация и ленивые загрузки начали бы «ходить» по кругу.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_favorites",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "tshirt_id"))
    private List<TShirt> favorites = new ArrayList<>();

    public List<TShirt> getFavorites() {
        return favorites;
    }

    public void setFavorites(List<TShirt> favorites) {
        this.favorites = favorites;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }
}
