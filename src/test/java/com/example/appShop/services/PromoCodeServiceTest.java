package com.example.appShop.services;

import com.example.appShop.dto.PromoCodeFormRequest;
import com.example.appShop.dto.PromoValidationDto;
import com.example.appShop.dto.PromoValidationRequest;
import com.example.appShop.dto.TotalsDto;
import com.example.appShop.entities.PromoCode;
import com.example.appShop.entities.TShirt;
import com.example.appShop.exceptions.ConflictException;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.mapper.PromoCodeMapper;
import com.example.appShop.repositories.PromoCodeRepository;
import com.example.appShop.repositories.TShirtRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Промокоды. Самое дорогое здесь — «код нельзя выдумать на фронтенде»:
 * скидку и суммы считает сервер, и тесты это фиксируют.
 */
@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private TShirtRepository tShirtRepository;

    private PromoCodeService service;

    @BeforeEach
    void setUp() {
        service = new PromoCodeService(promoCodeRepository, tShirtRepository, new PromoCodeMapper());
    }

    private static PromoCode withId(PromoCode promoCode, long id) {
        promoCode.setId(id);
        return promoCode;
    }

    private static PromoCode code(String value, int percent, boolean active, Instant expiresAt) {
        PromoCode promoCode = new PromoCode();
        promoCode.setId(1L);
        promoCode.setCode(value);
        promoCode.setDiscountPercent(percent);
        promoCode.setActive(active);
        promoCode.setExpiresAt(expiresAt);
        return promoCode;
    }

    private static TShirt shirt(long id, double price) {
        TShirt tShirt = new TShirt();
        tShirt.setId(id);
        tShirt.setName("Футболка " + id);
        tShirt.setPrice(price);
        tShirt.setAvailable(true);
        return tShirt;
    }

    @Test
    void createNormalizesCodeToUpperCase() {
        when(promoCodeRepository.existsByCodeIgnoreCase("SALE10")).thenReturn(false);
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(inv -> withId(inv.getArgument(0), 11L));

        var dto = service.create(new PromoCodeFormRequest(" sale10 ", 15, true, null));

        ArgumentCaptor<PromoCode> saved = ArgumentCaptor.forClass(PromoCode.class);
        verify(promoCodeRepository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("SALE10");
        assertThat(dto.discountPercent()).isEqualTo(15);
    }

    @Test
    void createGeneratesReadableCodeWhenAdminLeftItEmpty() {
        when(promoCodeRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(inv -> withId(inv.getArgument(0), 12L));

        var dto = service.create(new PromoCodeFormRequest("", 20, true, null));

        // код без похожих друг на друга символов (O/0, I/1) и без пробелов
        assertThat(dto.code()).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    void createRefusesDuplicateCode() {
        when(promoCodeRepository.existsByCodeIgnoreCase("SALE10")).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.create(new PromoCodeFormRequest("SALE10", 10, true, null)));
        verify(promoCodeRepository, never()).save(any(PromoCode.class));
    }

    @Test
    void updateKeepsCodeWhenAdminEditedOnlyPercent() {
        PromoCode existing = code("SALE10", 10, true, null);
        when(promoCodeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));

        var dto = service.update(1L, new PromoCodeFormRequest(null, 30, true, null));

        assertThat(dto.code()).isEqualTo("SALE10");
        assertThat(dto.discountPercent()).isEqualTo(30);
    }

    @Test
    void updateOfUnknownCodeGivesNotFound() {
        when(promoCodeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.update(404L, new PromoCodeFormRequest("X", 10, true, null)));
    }

    @Test
    void deleteRemovesCodeBecauseOrdersKeepTheirOwnSnapshot() {
        when(promoCodeRepository.findById(1L)).thenReturn(Optional.of(code("SALE10", 10, true, null)));

        service.delete(1L);

        verify(promoCodeRepository).delete(any(PromoCode.class));
    }

    @Test
    void validateRecalculatesTotalsFromDatabasePrices() {
        when(promoCodeRepository.findFirstByCodeIgnoreCase("SALE10"))
                .thenReturn(Optional.of(code("SALE10", 10, true, null)));
        // цены берём из базы, а не из корзины браузера: 100 + 150 = 250
        when(tShirtRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(shirt(1L, 100.0), shirt(2L, 150.0)));

        PromoValidationDto dto = service.validate(new PromoValidationRequest("sale10", List.of(1L, 2L)));

        TotalsDto totals = dto.totals();
        assertThat(totals.subtotal()).isCloseTo(250.0, offset(0.001));
        assertThat(totals.discountAmount()).isCloseTo(25.0, offset(0.001));
        assertThat(totals.total()).isCloseTo(225.0, offset(0.001));
        assertThat(dto.discountPercent()).isEqualTo(10);
        assertThat(dto.message()).contains("SALE10");
    }

    @Test
    void validateRejectsUnknownCode() {
        when(promoCodeRepository.findFirstByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.validate(new PromoValidationRequest("nope", List.of(1L))))
                .withMessageContaining("не найден");
    }

    @Test
    void validateRejectsCodeSwitchedOffByAdmin() {
        when(promoCodeRepository.findFirstByCodeIgnoreCase("SALE10"))
                .thenReturn(Optional.of(code("SALE10", 10, false, null)));

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.validate(new PromoValidationRequest("SALE10", List.of(1L))))
                .withMessageContaining("выключен");
    }

    @Test
    void validateRejectsExpiredCodeEvenIfItIsActive() {
        when(promoCodeRepository.findFirstByCodeIgnoreCase("SALE10"))
                .thenReturn(Optional.of(code("SALE10", 10, true, Instant.now().minus(1, ChronoUnit.DAYS))));

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> service.validate(new PromoValidationRequest("SALE10", List.of(1L))))
                .withMessageContaining("истёк");
    }

    @Test
    void resolveOrNullTreatsBlankCodeAsNoDiscount() {
        assertThat(service.resolveOrNull(null)).isNull();
        assertThat(service.resolveOrNull("   ")).isNull();
        verify(promoCodeRepository, never()).findFirstByCodeIgnoreCase(anyString());
    }

    @Test
    void resolveOrNullReturnsUsableCodeForOrder() {
        PromoCode active = code("SALE10", 10, true, Instant.now().plus(1, ChronoUnit.DAYS));
        when(promoCodeRepository.findFirstByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(active));

        assertThat(service.resolveOrNull("sale10")).isSameAs(active);
    }
}
