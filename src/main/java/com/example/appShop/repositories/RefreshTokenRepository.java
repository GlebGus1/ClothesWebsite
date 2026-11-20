package com.example.appShop.repositories;

import com.example.appShop.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserEmailAndRevokedFalse(String userEmail);

    /** Убирает «мусор»: просроченные записи не должны вечно расти в таблице. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :moment")
    int deleteAllExpired(@Param("moment") Instant moment);

    /** Отзыв всех активных токенов пользователя (например, смена пароля). */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.userEmail = :email AND t.revoked = false")
    int revokeAllByEmail(@Param("email") String email);
}
