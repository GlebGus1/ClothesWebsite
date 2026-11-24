package com.example.appShop.rest;

import com.example.appShop.dto.PromoCodeDto;
import com.example.appShop.dto.PromoCodeFormRequest;
import com.example.appShop.services.PromoCodeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Раздел админки «Промокоды». Весь путь /api/admin/** закрыт ролью ADMIN
 * в SecurityConfig, поэтому проверок прав внутри нет.
 *
 * POST принимает код пустым — сервис сгенерирует случайный читаемый код.
 */
@RestController
@RequestMapping("/api/admin/promo-codes")
public class AdminPromoCodeController {

    private final PromoCodeService promoCodeService;

    public AdminPromoCodeController(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    @GetMapping
    public List<PromoCodeDto> list() {
        return promoCodeService.list();
    }

    @PostMapping
    public ResponseEntity<PromoCodeDto> create(@Valid @RequestBody PromoCodeFormRequest form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promoCodeService.create(form));
    }

    @PutMapping("/{id}")
    public PromoCodeDto update(@PathVariable long id, @Valid @RequestBody PromoCodeFormRequest form) {
        return promoCodeService.update(id, form);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        promoCodeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
