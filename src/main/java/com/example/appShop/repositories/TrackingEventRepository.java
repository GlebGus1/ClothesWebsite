package com.example.appShop.repositories;

import com.example.appShop.entities.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * События аналитики (воронка продаж).
 *
 * existsByDedupeKey — вся защита от накрутки: один и тот же посетитель + товар + день
 * даёт ровно одно событие. Дубликаты срезает и уникальный индекс uk_tracking_dedupe,
 * но проверка перед вставкой спасает от лишней ошибки целостности на гонке.
 */
@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    long countByEventType(TrackingEvent.EventType eventType);

    @Query("select count(e) from TrackingEvent e where e.eventType = :type and e.createdAt >= :since")
    long countByTypeSince(@Param("type") TrackingEvent.EventType type, @Param("since") Instant since);
}
