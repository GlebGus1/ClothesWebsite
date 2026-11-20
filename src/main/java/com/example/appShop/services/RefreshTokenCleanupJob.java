package com.example.appShop.services;

import com.example.appShop.security.RefreshTokenService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Раз в сутки вычищаем просроченные refresh-токены, чтобы таблица не росла бесконечно.
 * (Отзываемые/активные записи не трогаем — только те, у которых expires_at в прошлом.)
 */
@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanupJob(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(fixedDelayString = "${app.refresh-token.cleanup-delay-ms:86400000}",
            initialDelayString = "${app.refresh-token.initial-delay-ms:60000}")
    public void cleanup() {
        refreshTokenService.cleanupExpired();
    }
}
