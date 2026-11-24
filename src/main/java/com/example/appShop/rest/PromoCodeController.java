package com.example.appShop.rest;

import com.example.appShop.dto.PromoValidationDto;
import com.example.appShop.dto.PromoValidationRequest;
import com.example.appShop.services.PromoCodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Проверка промокода покупателем (поле «Промокод» в корзине).
 *
 * POST, а не GET: мы не хотим, чтобы введённый код оседал в истории браузера и
 * в логах прокси как часть URL. Ответ содержит только сумму и процент — сам код
 * никто не «подбирает» массово, потому что ручка доступна только вошедшим.
 */
@RestController
@RequestMapping("/api/promo-codes")
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    public PromoCodeController(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    @PostMapping("/validate")
    public PromoValidationDto validate(@Valid @RequestBody PromoValidationRequest request) {
        return promoCodeService.validate(request);
    }
}
