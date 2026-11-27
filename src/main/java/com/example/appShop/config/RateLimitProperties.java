package com.example.appShop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки защиты от ботов и DDoS (всё под ключом security.rate-limit.*).
 *
 * Лимиты «на IP в памяти» — это первая линия обороны: она дешёвая и отрезает
 * перебор паролей до того, как запрос дойдёт до БД и до дорогого BCrypt.
 * Полноценную защиту от распределённой атаки даёт Cloudflare (он режет трафик
 * до нашего сервера) + капча Turnstile на регистрации.
 *
 * enabled=false — чтобы отключить лимиты совсем: например, в интеграционных тестах,
 * которые гоняют десятки запросов подряд с одного адреса (там это свойство ставят
 * через @TestPropertySource), или при ручной проверке «всего подряд» без пауз.
 */
@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,

        /** Публичные GET каталога: обычный человек за минуту не делает и 30 запросов к товарам. */
        @DefaultValue("80") int publicRequestsPerMinute,

        /** Логин и регистрация: 5 в минуту на IP — этого хватает живому человеку, но брутфорс встанет. */
        @DefaultValue("5") int authRequestsPerMinute,

        /** На сколько минут IP «запирается» после превышения лимита (= длительность окна, см. ниже). */
        @DefaultValue("1") int blockMinutes,

        /** Через сколько минут чистим карту счётчиков (иначе она росла бы бесконечно). */
        @DefaultValue("10") int sweepMinutes,

        /** Читать X-Forwarded-For (true, если сервис стоит за Cloudflare/nginx). */
        @DefaultValue("false") boolean trustProxyHeaders
) {
}
