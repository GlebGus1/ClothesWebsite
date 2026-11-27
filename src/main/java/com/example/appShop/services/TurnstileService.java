package com.example.appShop.services;

import com.example.appShop.config.TurnstileProperties;
import com.example.appShop.dto.CaptchaStatusDto;
import com.example.appShop.exceptions.InvalidRequestException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Проверка «ты не робот» через Cloudflare Turnstile на РЕГИСТРАЦИИ.
 *
 * Как это работает (простыми словами):
 *  1) фронтенд рисует виджет Turnstile и получает короткую строку — токен;
 *  2) этот токен приходит вместе с формой регистрации;
 *  3) мы ОДИН раз отправляем его на siteverify Cloudflare уже СВОЕЙ стороной
 *     (секретный ключ никогда не попадает в браузер);
 *  4) если success=false — регистрацию не проводим.
 *
 * Почему сервер, а не только виджет: любую форму можно отправить мимо страницы
 * (curl/Postman). Капча во фронтенде — это удобство, защита — только здесь.
 *
 * Если TURNSTILE_SECRET_KEY не задан — капча выключена (isConfigured()=false),
 * и регистрация работает как раньше: это нужное поведение для локальной разработки,
 * CI и тестов, где внешнего доступа к Cloudflare нет.
 */
@Service
public class TurnstileService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);

    private final TurnstileProperties properties;
    private final RestTemplate restTemplate;

    public TurnstileService(TurnstileProperties properties) {
        this.properties = properties;
        // Таймаут обязателен: без него «зависший» Cloudflare парализует регистрацию —
        // поток Tomcat ждал бы ответа бесконечно и весь сайт лёг бы из-за чужого сервиса.
        // (В Spring Boot 3.1, то есть Spring Framework 6.0, более современного RestClient
        // ещё нет, поэтому берём классический RestTemplate.)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.max(500, properties.timeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restTemplate = new RestTemplate(factory);
    }

    /** Публичная подсказка фронтенду: рисовать ли виджет и каким публичным ключом. */
    public CaptchaStatusDto status() {
        if (!properties.isConfigured()) {
            return new CaptchaStatusDto(false, null,
                    "Капча выключена: не задан TURNSTILE_SECRET_KEY в настройках сервера");
        }
        return new CaptchaStatusDto(true, properties.siteKey(), null);
    }

    /**
     * Обязательная проверка перед созданием аккаунта.
     * Бросает InvalidRequestException (400) — фронтенд показывает сообщение в форме.
     */
    public void verifyOrThrow(String captchaToken, String clientIp) {
        if (!properties.isConfigured()) {
            return;
        }
        if (captchaToken == null || captchaToken.isBlank()) {
            throw new InvalidRequestException("Подтвердите, что вы не робот (включите капчу в браузере)");
        }

        TurnstileVerifyResponse response;
        try {
            response = send(captchaToken, clientIp);
        } catch (RuntimeException e) {
            log.warn("Cloudflare Turnstile недоступен: {}", e.getMessage());
            // Честнее не пускать дальше, чем «сделать вид, что проверили».
            throw new InvalidRequestException("Сервер защиты от роботов недоступен. Попробуйте чуть позже.");
        }

        if (response == null || !response.success()) {
            log.info("Turnstile отклонил регистрацию (ip={}, коды={})", clientIp,
                    response == null ? List.of() : response.errorCodes());
            throw new InvalidRequestException("Капча не пройдена. Обновите страницу и попробуйте снова.");
        }
    }

    /**
     * Один запрос к Cloudflare.
     *
     * Метод не private намеренно: в юнит-тесте его переопределяют/мокают (см.
     * TurnstileServiceTest в этом же пакете), чтобы проверка капчи тестировалась
     * без реальных обращений в интернет.
     */
    TurnstileVerifyResponse send(String captchaToken, String clientIp) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.secretKey());
        form.add("response", captchaToken);
        if (clientIp != null && !clientIp.isBlank()) {
            form.add("remoteip", clientIp);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        return restTemplate.postForObject(properties.verifyUrl(),
                new HttpEntity<>(form, headers), TurnstileVerifyResponse.class);
    }

    /** Ответ Cloudflare: нас интересуют success и список кодов ошибки (для лога). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TurnstileVerifyResponse(boolean success, List<String> errorCodes) {

        TurnstileVerifyResponse {
            errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
        }
    }
}
