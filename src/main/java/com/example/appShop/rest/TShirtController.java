package com.example.appShop.rest;

import com.example.appShop.dto.PageResponse;
import com.example.appShop.dto.TShirtDetailsDto;
import com.example.appShop.dto.TShirtDto;
import com.example.appShop.dto.TShirtFormRequest;
import com.example.appShop.security.UserPrincipal;
import com.example.appShop.services.TShirtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Каталог. Чтение — публичное (гости видят товары и картинки),
 * изменение — только ADMIN (настроено в SecurityConfig).
 *
 * Обращаем внимание: entity TShirt в ответе не появляется, только DTO.
 */
@RestController
@RequestMapping("/api/tshirts")
public class TShirtController {

    private final TShirtService tShirtService;

    public TShirtController(TShirtService tShirtService) {
        this.tShirtService = tShirtService;
    }

    /**
     * GET /api/tshirts?size=M&available=true&sort=price_asc&page=0&pageSize=12
     * Флагом includeDisabled=true может пользоваться только админ (скрытые товары).
     */
    @GetMapping
    public PageResponse<TShirtDto> list(@RequestParam(required = false) String size,
                                        @RequestParam(required = false) Boolean available,
                                        @RequestParam(required = false) String sort,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "12") int pageSize,
                                        @RequestParam(defaultValue = "false") boolean includeDisabled,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        boolean canSeeDisabled = principal != null && principal.isAdmin();
        return PageResponse.from(tShirtService.search(size, available, sort, page, pageSize,
                includeDisabled && canSeeDisabled));
    }

    @GetMapping("/sizes")
    public List<String> sizes() {
        return tShirtService.availableSizes();
    }

    @GetMapping("/{id}")
    public TShirtDetailsDto getOne(@PathVariable long id) {
        return tShirtService.getDetails(id);
    }

    /**
     * Создание товара вместе с картинками (multipart/form-data):
     * part "data" — JSON с полями товара, part "files" — сами файлы (можно несколько).
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

    @PatchMapping("/{id}/preview/{imageId}")
    public TShirtDetailsDto setPreview(@PathVariable long id, @PathVariable long imageId) {
        return tShirtService.setPreviewImage(id, imageId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        tShirtService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
