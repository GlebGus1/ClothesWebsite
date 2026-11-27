package com.example.appShop.services;

import com.example.appShop.dto.TrackingRequest;
import com.example.appShop.entities.TrackingEvent;
import com.example.appShop.entities.TrackingEvent.EventType;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.TrackingEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Аналитика воронки: как считается событие и почему накрутка не работает.
 *
 * Ключевое правило, которое охраняют эти тесты: один и тот же посетитель + товар + день
 * дают РОВНО одну запись (dedupe_key), и только вместе с ней растёт views_count.
 * Если это правило сломать, «статистика» в админке начнёт врать, а админ будет
 * принимать решения по выдуманным цифрам.
 */
@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    @Mock
    private TrackingEventRepository trackingEventRepository;
    @Mock
    private TrackingEventWriter writer;
    @Mock
    private TShirtRepository tShirtRepository;

    @InjectMocks
    private TrackingService trackingService;

    private static String today() {
        return LocalDate.now().toString();
    }

    private TrackingEvent captureSaved() {
        ArgumentCaptor<TrackingEvent> captor = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(writer).insert(captor.capture(), any());
        return captor.getValue();
    }

    @Test
    void visitGetsDedupedKeyWithoutProductId() {
        when(trackingEventRepository.existsByDedupeKey("visit:" + today() + ":-:visitor-1")).thenReturn(false);

        boolean recorded = trackingService.recordVisit(new TrackingRequest("visitor-1", null), null);

        assertThat(recorded).isTrue();
        TrackingEvent event = captureSaved();
        assertThat(event.getEventType()).isEqualTo(EventType.SITE_VISIT);
        assertThat(event.getTshirtId()).isNull();
        assertThat(event.getVisitorKey()).isEqualTo("visitor-1");
        assertThat(event.getDedupeKey()).isEqualTo("visit:" + today() + ":-:visitor-1");
    }

    @Test
    void repeatedVisitOfSameDayIsNotCountedTwice() {
        when(trackingEventRepository.existsByDedupeKey("visit:" + today() + ":-:visitor-1")).thenReturn(true);

        boolean recorded = trackingService.recordVisit(new TrackingRequest("visitor-1", null), null);

        assertThat(recorded).isFalse();
        // F5 и возврат на страницу не должны плодить записи и лишний INSERT
        verify(writer, never()).insert(any(), any());
    }

    @Test
    void productViewCountsEventAndProductCounterInOneStep() {
        when(tShirtRepository.existsById(7L)).thenReturn(true);
        when(trackingEventRepository.existsByDedupeKey("view:" + today() + ":7:visitor-1")).thenReturn(false);

        boolean recorded = trackingService.recordProductView(new TrackingRequest("visitor-1", 7L), null);

        assertThat(recorded).isTrue();
        TrackingEvent event = captureSaved();
        assertThat(event.getEventType()).isEqualTo(EventType.PRODUCT_VIEW);
        assertThat(event.getTshirtId()).isEqualTo(7L);
        // second argument of insert() = товар, у которого должен вырасти views_count
        verify(writer).insert(any(TrackingEvent.class), eq(7L));
    }

    @Test
    void viewOfUnknownProductIsRejectedAndNothingIsWritten() {
        when(tShirtRepository.existsById(999L)).thenReturn(false);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> trackingService.recordProductView(new TrackingRequest("visitor-1", 999L), null));

        verify(writer, never()).insert(any(), any());
    }

    @Test
    void viewWithoutProductIdIsRejected() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> trackingService.recordProductView(new TrackingRequest("visitor-1", null), null));

        verify(writer, never()).insert(any(), any());
    }

    @Test
    void cartAddUsesItsOwnDedupeBucket() {
        when(tShirtRepository.existsById(3L)).thenReturn(true);
        when(trackingEventRepository.existsByDedupeKey("cart:" + today() + ":3:visitor-1")).thenReturn(false);

        assertThat(trackingService.recordCartAdd(new TrackingRequest("visitor-1", 3L), null)).isTrue();

        // добавление в корзину — не «просмотр»: у события свой префикс, и счётчик просмотров
        // товара при этом не трогается (второй аргумент insert = null)
        verify(writer).insert(any(TrackingEvent.class), isNull());
        verify(tShirtRepository, never()).increaseViewsCount(anyLong());
    }

    @Test
    void raceBetweenTwoIdenticalRequestsIsNotAnErrorForClient() {
        when(tShirtRepository.existsById(7L)).thenReturn(true);
        when(trackingEventRepository.existsByDedupeKey("view:" + today() + ":7:visitor-1")).thenReturn(false);
        // параллельный запрос успел вставить такой же dedupe_key раньше нас
        doThrow(new DataIntegrityViolationException("uk_tracking_dedupe"))
                .when(writer).insert(any(TrackingEvent.class), eq(7L));

        boolean recorded = trackingService.recordProductView(new TrackingRequest("visitor-1", 7L), null);

        // уникальный индекс сделал своё дело: событие «уже учтено», а не 500 error
        assertThat(recorded).isFalse();
    }

    @Test
    void visitorKeyIsSanitizedBecauseItComesFromBrowser() {
        when(trackingEventRepository.existsByDedupeKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        trackingService.recordVisit(new TrackingRequest("  vi$si*tor+1<script>  ", null), null);

        TrackingEvent event = captureSaved();
        // ключ приходит из недоверенного браузера: остаются только «безопасные» символы
        assertThat(event.getVisitorKey()).isEqualTo("visitor1script");
    }

    @Test
    void veryLongVisitorKeyIsCutToColumnSize() {
        when(trackingEventRepository.existsByDedupeKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        String huge = "a".repeat(200);

        trackingService.recordVisit(new TrackingRequest(huge, null), null);

        // колонка visitor_key — varchar(64); иначе база ответила бы ошибкой на «злой» ключ
        assertThat(captureSaved().getVisitorKey()).hasSize(64);
    }

    @Test
    void missingVisitorKeyFallsBackToAnonymizedIpAndNeverBecomesEmpty() {
        when(trackingEventRepository.existsByDedupeKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        trackingService.recordVisit(new TrackingRequest("   ", null), "ip1a2b3c4d");
        assertThat(captureSaved().getVisitorKey()).isEqualTo("ip1a2b3c4d");
    }

    @Test
    void fullyUnsafeVisitorKeyBecomesAnonInsteadOfBlank() {
        when(trackingEventRepository.existsByDedupeKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        trackingService.recordVisit(new TrackingRequest("@@@###", null), null);

        // пустой ключ склеил бы события разных посетителей в один «мусорный» дедуп-ключ
        assertThat(captureSaved().getVisitorKey()).isEqualTo("anon");
    }
}
