package com.example.appShop.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Refresh-токен в БД.
 *
 * Храним НЕ сам токен, а его SHA-256-хэш: если кто-то сможет прочитать таблицу
 * или сделать SQL-дамп, он не сможет начать пользоваться токенами.
 *
 * Зачем вообще таблица, если JWT сами по себе валидны? Потому что refresh-токен
 * должен иметь «право на отзыв» (logout, ротация, компрометация) — а stateless JWT
 * отозвать нельзя, пока он не истёк.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    /** На какой токен заменён этот (ротация) — полезно для аудита. */
    @Column(name = "replaced_by", length = 64)
    private String replacedBy;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isActive() {
        return !revoked && expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public String getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(String replacedBy) {
        this.replacedBy = replacedBy;
    }
}
