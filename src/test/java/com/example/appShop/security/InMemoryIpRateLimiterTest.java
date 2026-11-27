package com.example.appShop.security;

import com.example.appShop.config.RateLimitProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Лимитчик «запросов на IP» (защита от перебора паролей и ботов).
 *
 * Время здесь подменяют фиксированным (см. epochSeconds() у лимитера): иначе тест
 * зависел бы от того, на какую секунду минуты он «пришёл», и падал бы раз в минуту.
 */
class InMemoryIpRateLimiterTest {

    /** Стартуем ровно с середины минуты, чтобы край окна не попал внутрь теста. */
    private static final long START_OF_MINUTE = 1_700_000_030L;

    private static RateLimitProperties props(int authPerMinute, int publicPerMinute) {
        return new RateLimitProperties(true, publicPerMinute, authPerMinute, 1, 10, false);
    }

    private static final class FixedTimeLimiter extends InMemoryIpRateLimiter {

        private long now = START_OF_MINUTE;

        private FixedTimeLimiter(RateLimitProperties properties) {
            super(properties);
        }

        @Override
        protected long epochSeconds() {
            return now;
        }

        private void advanceSeconds(long seconds) {
            now += seconds;
        }
    }

    @Test
    void loginAllowsFiveAttemptsAndBlocksSixth() {
        FixedTimeLimiter limiter = new FixedTimeLimiter(props(5, 80));

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(limiter.checkAuthEndpoint("203.0.113.7").allowed())
                    .as("попытка входа №" + attempt)
                    .isTrue();
        }

        InMemoryIpRateLimiter.Decision blocked = limiter.checkAuthEndpoint("203.0.113.7");
        assertThat(blocked.allowed()).isFalse();
        // Retry-After честный: клиенту говорят, сколько реально ждать, а не «попробуйте позже»
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void blockExpiresWhenWindowRollsOver() {
        FixedTimeLimiter limiter = new FixedTimeLimiter(props(5, 80));
        for (int attempt = 0; attempt < 6; attempt++) {
            limiter.checkAuthEndpoint("203.0.113.7");
        }
        assertThat(limiter.checkAuthEndpoint("203.0.113.7").allowed()).isFalse();

        limiter.advanceSeconds(60);

        // новое окно — счётчик обнуляется, и 6-я попытка снова обычная попытка
        assertThat(limiter.checkAuthEndpoint("203.0.113.7").allowed()).isTrue();
    }

    @Test
    void limitCountsPerIpNotGlobally() {
        FixedTimeLimiter limiter = new FixedTimeLimiter(props(5, 80));
        for (int attempt = 0; attempt < 6; attempt++) {
            limiter.checkAuthEndpoint("203.0.113.7");
        }

        // другой адрес не должен «отгребать» за соседа (иначе NAT/офис остался бы без доступа)
        assertThat(limiter.checkAuthEndpoint("198.51.100.4").allowed()).isTrue();
    }

    @Test
    void browsingCatalogDoesNotBurnLoginBudget() {
        FixedTimeLimiter limiter = new FixedTimeLimiter(props(5, 80));

        for (int request = 0; request < 40; request++) {
            assertThat(limiter.checkPublicApi("203.0.113.7").allowed()).isTrue();
        }

        // ключевое: корзины «публичные» и «логин» разделены (разные scope в одном лимитере)
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(limiter.checkAuthEndpoint("203.0.113.7").allowed())
                    .as("после 40 запросов каталога попытка входа №" + attempt)
                    .isTrue();
        }
    }

    @Test
    void registerAndLoginHaveSeparateBudgets() {
        FixedTimeLimiter limiter = new FixedTimeLimiter(props(5, 80));
        for (int attempt = 0; attempt < 6; attempt++) {
            limiter.checkRegistration("203.0.113.7");
        }

        assertThat(limiter.checkRegistration("203.0.113.7").allowed()).isFalse();
        // спам-регистрации не должны блокировать вход существующему клиенту
        assertThat(limiter.checkAuthEndpoint("203.0.113.7").allowed()).isTrue();
    }

    @Test
    void publicApiStopsOnEightyFirstRequestPerMinute() {
        FixedTimeLimiter limiter = new FixedTimeLimiter(props(5, 80));

        for (int request = 1; request <= 80; request++) {
            assertThat(limiter.checkPublicApi("203.0.113.7").allowed())
                    .as("запрос к каталогу №" + request)
                    .isTrue();
        }

        assertThat(limiter.checkPublicApi("203.0.113.7").allowed()).isFalse();
    }

    @Test
    void disabledLimiterLetsEverythingThrough() {
        RateLimitProperties off = new RateLimitProperties(false, 1, 1, 1, 10, false);
        FixedTimeLimiter limiter = new FixedTimeLimiter(off);

        for (int request = 0; request < 50; request++) {
            assertThat(limiter.checkAuthEndpoint("203.0.113.7").allowed()).isTrue();
        }
    }

    @Test
    void unknownClientIsNotPunishedByNullKey() {
        // если адрес определить не удалось (внутренний вызов, тест), лимит применять нельзя:
        // иначе один «безымянный» источник заблокировал бы всех подряд
        FixedTimeLimiter limiter = new FixedTimeLimiter(props(5, 80));

        for (int request = 0; request < 20; request++) {
            assertThat(limiter.checkAuthEndpoint(null).allowed()).isTrue();
        }
    }

    @Test
    void resetClearsCounters() {
        FixedTimeLimiter limiter = new FixedTimeLimiter(props(1, 1));
        limiter.checkAuthEndpoint("203.0.113.7");
        assertThat(limiter.checkAuthEndpoint("203.0.113.7").allowed()).isFalse();

        limiter.reset();

        assertThat(limiter.currentHits("auth", "203.0.113.7")).isZero();
        assertThat(limiter.checkAuthEndpoint("203.0.113.7").allowed()).isTrue();
    }
}
