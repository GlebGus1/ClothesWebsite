package com.example.appShop.rest;

import com.example.appShop.dto.PageResponse;
import com.example.appShop.dto.TShirtDetailsDto;
import com.example.appShop.services.AdminProductCatalogService;
import com.example.appShop.services.TShirtService;
import com.example.appShop.dto.AdminTShirtDto;
import com.example.appShop.dto.AvailabilityRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AdminProductCatalogService adminCatalog;

    public AdminTShirtController(TShirtService tShirtService, AdminProductCatalogService adminCatalog) {
        this.tShirtService = tShirtService;
        this.adminCatalog = adminCatalog;
    }

    /**
     * Таблица «Товары (админ)»: тут есть viewsCount и ссылка на заказ, в котором
     * ушёл товар, — публичный каталог таких данных не показывает.
     */
    @GetMapping
    public PageResponse<AdminTShirtDto> list(@RequestParam(required = false) String size,
                                             @RequestParam(required = false) Boolean available,
                                             @RequestParam(required = false) Long collectionId,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int pageSize,
                                             @RequestParam(defaultValue = "true") boolean includeDisabled) {
        return PageResponse.from(adminCatalog.search(size, available, collectionId, sort, page, pageSize,
                includeDisabled));
    }

    /** Карточка любой позиции, включая скрытую: нужна, чтобы открыть форму правки. */
    @GetMapping("/{id}")
    public TShirtDetailsDto getOne(@PathVariable long id) {
        return tShirtService.getDetailsForAdmin(id);
    }

    /**
     * Быстрое «в продаже / нет в продаже» прямо из таблицы — без открытия формы
     * редактирования товара.
     */
    @PatchMapping("/{id}/availability")
    public TShirtDetailsDto setAvailability(@PathVariable long id,
                                            @Valid @RequestBody AvailabilityRequest request) {
        return adminCatalog.setAvailability(id, request.isAvailable());
    }

    /** Быстрый выбор главной картинки товара (превью в каталоге). */
    @PatchMapping("/{id}/preview/{imageId}")
    public TShirtDetailsDto setPreview(@PathVariable long id, @PathVariable long imageId) {
        return tShirtService.setPreviewImage(id, imageId);
    }

    /** Снять флаг «скрыт» и вернуть позицию в продажу. */
    @PatchMapping("/{id}/restore")
    public TShirtDetailsDto restore(@PathVariable long id) {
        return tShirtService.restore(id);
    }
}
