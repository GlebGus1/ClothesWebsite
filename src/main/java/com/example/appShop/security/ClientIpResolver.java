package com.example.appShop.security;

import com.example.appShop.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Как честно определить IP посетителя.
 *
 * Заголовок X-Forwarded-For пришёл от клиента, поэтому доверять ему «всегда» нельзя:
 * атакующий мог бы подставлять новый IP в каждом запросе и обходить любой лимит по IP.
 * Доверять ему можно только когда приложение реально стоит за прокси (Cloudflare/nginx),
 * и тогда берём ПЕРВЫЙ адрес из списка — его поставил внешний прокси.
 *
 * Флаг trust-proxy-headers вынесен в настройки (security.rate-limit.trust-proxy-headers),
 * по умолчанию выключен: локальный запуск и тесты не должны «верить» заголовкам.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        if (properties.trustProxyHeaders()) {
            String fromProxy = firstForwardedValue(request.getHeader(FORWARDED_FOR));
            if (fromProxy == null) {
                fromProxy = trimToNull(request.getHeader(REAL_IP));
            }
            if (fromProxy != null) {
                return fromProxy;
            }
        }
        String remote = trimToNull(request.getRemoteAddr());
        return remote == null ? "unknown-host" : remote;
    }

    /** X-Forwarded-For — это список «клиент, прокси1, прокси2»: первый элемент и есть исходный адрес. */
    private String firstForwardedValue(String header) {
        String value = trimToNull(header);
        if (value == null) {
            return null;
        }
        for (String part : value.split(",")) {
            String candidate = trimToNull(part);
            if (candidate != null && !UNKNOWN.equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
