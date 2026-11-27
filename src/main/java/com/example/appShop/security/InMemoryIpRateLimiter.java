package com.example.appShop.security;

import com.example.appShop.config.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Простейший rate limiter «IP + окно в одну минуту» прямо в памяти процесса.
 *
 * Как это работает (простыми словами):
 *  - для каждого «ключ + минута» держим счётчик в ConcurrentHashMap;
 *  - запрос пришёл — увеличили счётчик; если счётчик больше лимита — отказ (429);
 *  - окно фиксированное (границы — целые минуты): это чуть грубее скользящего окна
 *    («2 лимита на стыке минут» возможны), зато не надо хранить список отметок
 *    каждого запроса, а значит память не растёт вместе с трафиком;
 *  - карту периодически чистим, иначе после суток атаки она была бы забита ключами.
 *
 * Ограничение честно и о нём нужно помнить: счётчики живут в ПАМЯТИ ОДНОГО процесса.
 * Если приложение запустят в 5 копиярах за балансировщиком, лимиты будут «свои» для копии.
 * Для распределённой защиты нужен внешний слой (Cloudflare) или Redis-счётчик.
 */
@Component
public class InMemoryIpRateLimiter {

    /** Результат проверки: пропускать ли запрос и сколько секунд клиенту ждать. */
    public record Decision(boolean allowed, long retryAfterSeconds, int limit) {

        public static Decision allow(int limit) {
            return new Decision(true, 0, limit);
        }

        public static Decision block(long retryAfterSeconds, int limit) {
            return new Decision(false, retryAfterSeconds, limit);
        }
    }

    private static final class Window {

        /** Номер окна (epoch / его длительность): по нему понимаем, что счётчик «протух». */
        private final long windowIndex;
        private final AtomicInteger hits = new AtomicInteger();
        private volatile Instant lastTouch = Instant.now();

        private Window(long windowIndex) {
            this.windowIndex = windowIndex;
        }
    }

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    /** Когда в последний раз чистили карту (AtomicLong, чтобы чистку не делали сразу несколько потоков). */
    private final AtomicLong lastSweepEpochMs = new AtomicLong(System.currentTimeMillis());

    public InMemoryIpRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    public Decision checkPublicApi(String clientKey) {
        return check("public", clientKey, properties.publicRequestsPerMinute(), 1);
    }

    /**
     * Логин: окно = blockMinutes (по умолчанию 1 => ровно «5 попыток в минуту», как и просили).
     * Если поставить blockMinutes=5, после пяти попыток IP отсекается почти на 5 минут:
     * окно станет пятиминутным.
     */
    public Decision checkAuthEndpoint(String clientKey) {
        return check("auth", clientKey, properties.authRequestsPerMinute(), properties.blockMinutes());
    }

    public Decision checkRegistration(String clientKey) {
        return check("register", clientKey, properties.authRequestsPerMinute(), 1);
    }

    /**
     * @param scope разделяет корзины: лимит логина не должен заканчиваться из-за
     *              того, что пользователь листал каталог.
     */
    public Decision check(String scope, String clientKey, int limit, int windowMinutes) {
        if (!properties.enabled() || limit <= 0 || clientKey == null) {
            return Decision.allow(Math.max(limit, 1));
        }
        sweepIfNeeded();

        long windowSeconds = Math.max(1, windowMinutes) * 60L;
        long currentWindow = Math.floorDiv(epochSeconds(), windowSeconds);
        String key = scope + ':' + clientKey;

        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowIndex != currentWindow) {
                return new Window(currentWindow);
            }
            return existing;
        });
        window.lastTouch = Instant.now();

        int hits = window.hits.incrementAndGet();
        if (hits > limit) {
            long secondsLeftOfWindow = windowSeconds - Math.floorMod(epochSeconds(), windowSeconds);
            return Decision.block(Math.max(1L, secondsLeftOfWindow), limit);
        }
        return Decision.allow(limit);
    }

    /** Сколько запросов уже сделано за текущее окно (для юнит-тестов и диагностики). */
    public int currentHits(String scope, String clientKey) {
        Window window = windows.get(scope + ':' + clientKey);
        return window == null ? 0 : window.hits.get();
    }

    public void reset() {
        windows.clear();
    }

    private void sweepIfNeeded() {
        long now = System.currentTimeMillis();
        long previous = lastSweepEpochMs.get();
        long sweepPeriodMs = Duration.ofMinutes(Math.max(1, properties.sweepMinutes())).toMillis();
        if (now - previous < sweepPeriodMs) {
            return;
        }
        if (!lastSweepEpochMs.compareAndSet(previous, now)) {
            return; // чисткой занимается ровно один поток
        }
        Instant staleBefore = Instant.ofEpochMilli(now - sweepPeriodMs);
        windows.entrySet().removeIf(entry -> entry.getValue().lastTouch.isBefore(staleBefore));
    }

    /**
     * «Текущее время» в секундах. Метод сделан protected только ради юнит-теста:
     * лимитер привязан к реальным минутам, и без возможности подставить фиктивное
     * время тест «перевод стрелок» (окно закончилось — счётчик обнулился) было бы
     * невозможно написать без сна в секунду (а тесты не должны зависеть от часового
     * пояса и от того, на какую секунду они пришли).
     */
    protected long epochSeconds() {
        return Instant.now().getEpochSecond();
    }
}
