package com.example.appShop.rest;

import com.example.appShop.dto.TrackingRequest;
import com.example.appShop.security.VisitorKeyFactory;
import com.example.appShop.services.TrackingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Приёмник событий воронки продаж: визиты на сайт и добавления в корзину.
 *
 * Кто сюда стучится: фронтенд, и ТОЛЬКО после согласия пользователя в cookie-баннере
 * (см. frontend/src/utils/consent.ts и frontend/src/api/tracking.ts). Если человек
 * нажал «Отказываюсь», запросов сюда нет вообще — корзина, вход и оформление заказа
 * при этом работают как обычно.
 *
 * Просмотры карточек сюда НЕ шлются: их засчитывает сам GET /api/tshirts/{id}
 * по заголовку X-Visitor-Id (см. TShirtController#getOne). Если бы для этого была
 * отдельная ручка, один и тот же просмотр считался бы дважды.
 *
 * Ответ всегда 202 Accepted и никогда не ломает страницу: это статистика, а не бизнес-операция.
 * Лимит частоты запросов на /api/** (80/мин на адрес) защищает эту ручку от спама.
 */
@RestController
@RequestMapping("/api/track")
public class TrackingController {

    private final TrackingService trackingService;
    private final VisitorKeyFactory visitorKeyFactory;

    public TrackingController(TrackingService trackingService, VisitorKeyFactory visitorKeyFactory) {
        this.trackingService = trackingService;
        this.visitorKeyFactory = visitorKeyFactory;
    }

    @PostMapping("/visit")
    public ResponseEntity<Void> visit(@RequestBody(required = false) TrackingRequest request,
                                      HttpServletRequest httpRequest) {
        trackingService.recordVisit(safe(request), fallbackKey(httpRequest));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/cart-add")
    public ResponseEntity<Void> cartAdd(@RequestBody TrackingRequest request, HttpServletRequest httpRequest) {
        trackingService.recordCartAdd(safe(request), fallbackKey(httpRequest));
        return ResponseEntity.accepted().build();
    }

    private TrackingRequest safe(TrackingRequest request) {
        return request == null ? new TrackingRequest(null, null) : request;
    }

    /**
     * Если браузер не прислал свой visitorId, подставляем обезличенный отпечаток адреса
     * (VisitorKeyFactory): в базе оказывается не IP, а короткая несвязуемая метка.
     */
    private String fallbackKey(HttpServletRequest httpRequest) {
        return visitorKeyFactory.anonymizedIp(httpRequest.getRemoteAddr());
    }
}
