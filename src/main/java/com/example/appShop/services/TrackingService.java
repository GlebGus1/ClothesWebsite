package com.example.appShop.services;

import com.example.appShop.dto.TrackingRequest;
import com.example.appShop.entities.TrackingEvent;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.TrackingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Учёт событий воронки продаж: визит на сайт -> просмотр карточки -> добавление в корзину
 * -> оформленный заказ (заказ считается не здесь, а в AnalyticsService по таблице orders).
 *
 * Защита от накрутки (простыми словами). События принимает фронтенд, поэтому любой
 * желающий мог бы «накрутить» просмотры простыми повторами. Мы режем это на уровне базы:
 * у каждого события есть ключ dedupe_key = «тип | дата | товар | посетитель», и над ним
 * стоит УНИКАЛЬНЫЙ индекс. Значит:
 *   - одно и то же открытие карточки в тот же день (F5, возврат со страницы заказа,
 *     повторная закладка) даёт ровно одну запись и ровно +1 к views_count;
 *   - на следующий день просмотр засчитывается заново — это уже новое событие.
 * Проверка existsByDedupeKey спасает от лишней ошибки БД, а уникальный индекс — от гонки
 * двух параллельных запросов (тогда второй запрос приходит сюда же и молча считается
 * «уже учтённым»).
 *
 * Приватность: мы не пищем e-mail, имя, IP целиком или серверные куки. Только короткий
 * случайный идентификатор посетителя, который сам сгенерировал браузер и который живёт
 * в его localStorage. Записи обезличенные, а согласие пользователя спрашивает баннер
 * cookie (если человек отказался — фронтенд сюда вообще ничего не шлёт).
 */
@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    /** Ключи событий: «тип:дата:товар:посетитель». Товар/посетитель могут отсутствовать. */
    private static final String VISIT_PREFIX = "visit";
    private static final String VIEW_PREFIX = "view";
    private static final String CART_PREFIX = "cart";

    private final TrackingEventRepository trackingEventRepository;
    private final TrackingEventWriter writer;
    private final TShirtRepository tShirtRepository;

    public TrackingService(TrackingEventRepository trackingEventRepository,
                           TrackingEventWriter writer,
                           TShirtRepository tShirtRepository) {
        this.trackingEventRepository = trackingEventRepository;
        this.writer = writer;
        this.tShirtRepository = tShirtRepository;
    }

    /** Первый заход на сайт (одна запись на посетителя в сутки). */
    public boolean recordVisit(TrackingRequest request, String fallbackKey) {
        String visitorKey = normalizeVisitorKey(request, fallbackKey);
        return save(TrackingEvent.EventType.SITE_VISIT, visitorKey, null);
    }

    /**
     * Просмотр карточки товара. Тот же вызов засчитывает и +1 к tshirts.views_count —
     * обе записи в одной короткой транзакции (см. TrackingEventWriter).
     */
    public boolean recordProductView(TrackingRequest request, String fallbackKey) {
        String visitorKey = normalizeVisitorKey(request, fallbackKey);
        long tshirtId = requireProduct(request);
        return save(TrackingEvent.EventType.PRODUCT_VIEW, visitorKey, tshirtId);
    }

    /** Товар добавили в корзину (счётчик «намерение купить», тоже раз в день на пару). */
    public boolean recordCartAdd(TrackingRequest request, String fallbackKey) {
        String visitorKey = normalizeVisitorKey(request, fallbackKey);
        long tshirtId = requireProduct(request);
        return save(TrackingEvent.EventType.CART_ADD, visitorKey, tshirtId);
    }

    public long count(TrackingEvent.EventType type) {
        return trackingEventRepository.countByEventType(type);
    }

    private long requireProduct(TrackingRequest request) {
        Long tshirtId = request.tshirtId();
        if (tshirtId == null || tshirtId <= 0 || !tShirtRepository.existsById(tshirtId)) {
            throw new NotFoundException("Футболка", tshirtId == null ? 0L : tshirtId);
        }
        return tshirtId;
    }

    private boolean save(TrackingEvent.EventType type, String visitorKey, Long tshirtId) {
        String dedupeKey = dedupeKey(type, visitorKey, tshirtId);
        if (trackingEventRepository.existsByDedupeKey(dedupeKey)) {
            return false;
        }

        TrackingEvent event = new TrackingEvent();
        event.setEventType(type);
        event.setVisitorKey(visitorKey);
        event.setTshirtId(tshirtId);
        event.setDedupeKey(dedupeKey);

        try {
            writer.insert(event, type == TrackingEvent.EventType.PRODUCT_VIEW ? tshirtId : null);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Параллельный запрос успел вставить такой же ключ: это не ошибка клиента,
            // событие уже учтено.
            log.debug("Дубликат события аналитики {} — уже учтено", dedupeKey);
            return false;
        }
    }

    private String dedupeKey(TrackingEvent.EventType type, String visitorKey, Long tshirtId) {
        String prefix = switch (type) {
            case SITE_VISIT -> VISIT_PREFIX;
            case PRODUCT_VIEW -> VIEW_PREFIX;
            case CART_ADD -> CART_PREFIX;
        };
        String today = LocalDate.now().toString();
        String productPart = tshirtId == null ? "-" : String.valueOf(tshirtId);
        return prefix + ':' + today + ':' + productPart + ':' + visitorKey;
    }

    /**
     * Ключ посетителя приходит к нам из браузера, а значит из недоверенного источника.
     * Оставляем только безопасные символы и режем по длине колонки; если клиент
     * ключ не прислал — используем короткий анонимный отпечаток (см. fallbackKey).
     */
    private String normalizeVisitorKey(TrackingRequest request, String fallbackKey) {
        String raw = request.visitorOrDefault(fallbackKey).replaceAll("[^A-Za-z0-9_-]", "");
        if (raw.isBlank()) {
            raw = "anon";
        }
        return raw.length() <= 64 ? raw : raw.substring(0, 64);
    }
}
