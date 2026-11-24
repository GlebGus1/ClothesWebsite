package com.example.appShop.rest;

import com.example.appShop.dto.TShirtDetailsDto;
import com.example.appShop.services.TShirtService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Товарные операции администратора, которые нельзя давать гостям.
 *
 * Почему отдельный контроллер, а не параметр в публичном GET /api/tshirts/{id}:
 * публичная карточка обязана навсегда прятать снятые с продажи товары (is_disabled=true),
 * и «разрешить админу» в ней означало бы ветвление в самом чувствительном к приватности
 * эндпоинте. Здесь же весь путь /api/admin/** закрыт ролью ADMIN в SecurityConfig,
 * поэтому внутри контроллера проверок прав нет вообще.
 */
@RestController
@RequestMapping("/api/admin/tshirts")
public class AdminTShirtController {

    private final TShirtService tShirtService;

    public AdminTShirtController(TShirtService tShirtService) {
        this.tShirtService = tShirtService;
    }

    /** Карточка любой позиции, включая скрытую: нужна, чтобы открыть форму правки. */
    @GetMapping("/{id}")
    public TShirtDetailsDto getOne(@PathVariable long id) {
        return tShirtService.getDetailsForAdmin(id);
    }

    /** Снять флаг «скрыт» и вернуть позицию в продажу. */
    @PatchMapping("/{id}/restore")
    public TShirtDetailsDto restore(@PathVariable long id) {
        return tShirtService.restore(id);
    }
}
