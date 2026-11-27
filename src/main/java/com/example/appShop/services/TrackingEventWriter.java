package com.example.appShop.services;

import com.example.appShop.entities.TrackingEvent;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.TrackingEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Запись события аналитики в отдельной транзакции (REQUIRES_NEW).
 *
 * Зачем нужен отдельный класс, а не метод внутри TrackingService:
 *  1) аннотация @Transactional работает только через прокси, то есть её НЕЛЬЗЯ
 *     «вызвать у самого себя» (this.insert(...) игнорируется);
 *  2) вставка обязана быть своей собственной транзакцией, потому что при гонке двух
 *     одинаковых запросов база ответит уникальным индексом uk_tracking_dedupe, а
 *     участие в «чужой» транзакции пометило бы её rollback-only — и тогда внешний
 *     метод не смог бы спокойно вернуть «уже засчитано».
 *
 * Заодно здесь же, в этой же короткой транзакции, растёт счётчик просмотров товара:
 * событие и счётчик либо записаны вместе, либо не записаны вовсе.
 */
@Service
public class TrackingEventWriter {

    private final TrackingEventRepository trackingEventRepository;
    private final TShirtRepository tShirtRepository;

    public TrackingEventWriter(TrackingEventRepository trackingEventRepository,
                               TShirtRepository tShirtRepository) {
        this.trackingEventRepository = trackingEventRepository;
        this.tShirtRepository = tShirtRepository;
    }

    /**
     * @param countedTShirtId для просмотра товара — id, у которого растёт views_count;
     *                        null — событие ни к чему не «привязано» (визит на сайт).
     * При гонке параллельных запросов отсюда прилетит DataIntegrityViolationException,
     * а ловит и «сглатывает» его TrackingService — там, где транзакция уже закрыта.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(TrackingEvent event, Long countedTShirtId) {
        trackingEventRepository.saveAndFlush(event);
        if (countedTShirtId != null) {
            tShirtRepository.increaseViewsCount(countedTShirtId);
        }
    }
}
