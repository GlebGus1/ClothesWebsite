package com.example.appShop.rest;

import com.example.appShop.dto.PageResponse;
import com.example.appShop.dto.TShirtDetailsDto;
import com.example.appShop.dto.TShirtDto;
import com.example.appShop.dto.TShirtFormRequest;
import com.example.appShop.dto.TrackingRequest;
import com.example.appShop.services.TShirtService;
import com.example.appShop.services.TrackingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Публичный каталог. Чтение — для гостей, изменение — только ADMIN.
 *
 * Разделение принципиальное:
 *  - этот контроллер умеет показывать ТОЛЬКО товары, которые не сняли с продажи
 *    (is_disabled = false). Прежний параметр includeDisabled отсюда убран: раньше
 *    «подсмотреть снятый товар» разрешалось прямо в методе по роли пользователя,
 *    и такая логика легко выезжает за пределы метода. Админские чтение, скрытие и
 *    возврат позиции идут отдельным путём /api/admin/tshirts/** (AdminTShirtController).
 *  - entity TShirt в ответы не попадает никогда, только DTO.
 */
@RestController
@RequestMapping("/api/tshirts")
public class TShirtController {

    private final TShirtService tShirtService;
    private final TrackingService trackingService;

    public TShirtController(TShirtService tShirtService, TrackingService trackingService) {
        this.tShirtService = tShirtService;
        this.trackingService = trackingService;
    }

    /**
     * GET /api/tshirts?size=M&available=true&collectionId=2&sort=price_asc&page=0&pageSize=12
     *
     * collectionId — фильтр «показать дроп N»: чипсы коллекций над каталогом.
     */
    @GetMapping
    public PageResponse<TShirtDto> search(@RequestParam(required = false) String size,
                                          @RequestParam(required = false) Boolean available,
                                          @RequestParam(required = false) Long collectionId,
                                          @RequestParam(required = false) String sort,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "12") int pageSize) {
        return PageResponse.from(tShirtService.search(size, available, collectionId, sort, page, pageSize, false));
    }

    @GetMapping("/sizes")
    public List<String> sizes() {
        return tShirtService.availableSizes();
    }

    /**
     * Карточка товара. Скрытая позиция (is_disabled = true) недоступна и здесь: сервис
     * кинет NotFound → 404, поэтому прямой запрос по id ничего не даёт.
     *
     * +1 просмотр считается ТОЛЬКО если клиент прислал заголовок X-Visitor-Id.
     * Фронтенд добавляет его исключительно после согласия посетителя в cookie-баннере,
     * поэтому «согласия нет» = «счётчик не растёт», и серверу не нужно ничего знать
     * про куки. Анти-фрод: не больше одного засчитанного просмотра карточки на товар
     * на одного посетителя в сутки (уникальный ключ в таблице tracking_events).
     */
    @GetMapping("/{id}")
    public TShirtDetailsDto getOne(@PathVariable long id,
                                   @RequestHeader(name = "X-Visitor-Id", required = false) String visitorId) {
        TShirtDetailsDto details = tShirtService.getDetails(id);
        if (visitorId != null && !visitorId.isBlank()) {
            trackingService.recordProductView(new TrackingRequest(visitorId, id), null);
        }
        return details;
    }

    /**
     * Создание товара вместе с картинками (multipart/form-data):
     * part "data" — JSON с полями товара, part "files" — сами файлы (можно несколько).
     * Путь доступен только ADMIN (SecurityConfig), поэтому проверок роли в методе нет.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TShirtDetailsDto create(@RequestPart("data") @Valid TShirtFormRequest data,
                                   @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return tShirtService.create(data, files);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TShirtDetailsDto update(@PathVariable long id,
                                   @RequestPart("data") @Valid TShirtFormRequest data,
                                   @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return tShirtService.update(id, data, files);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        tShirtService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
