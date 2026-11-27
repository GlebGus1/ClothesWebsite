package com.example.appShop.rest;

import com.example.appShop.dto.CollectionDto;
import com.example.appShop.dto.CollectionFormRequest;
import com.example.appShop.services.CollectionService;
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
 * Коллекции (дропы) в админке: создать, переименовать, перекрасить бейдж,
 * выключить полку, удалить.
 *
 * Про удаление важно: товары НЕ удаляются, они просто остаются без коллекции
 * (см. CollectionService.delete) — на них уже могут ссылаться заказы.
 * Весь путь /api/admin/** закрыт ролью ADMIN в SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/collections")
public class AdminCollectionController {

    private final CollectionService collectionService;

    public AdminCollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @GetMapping
    public List<CollectionDto> list() {
        return collectionService.findAll();
    }

    @PostMapping
    public ResponseEntity<CollectionDto> create(@Valid @RequestBody CollectionFormRequest form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collectionService.create(form));
    }

    @PutMapping("/{id}")
    public CollectionDto update(@PathVariable long id, @Valid @RequestBody CollectionFormRequest form) {
        return collectionService.update(id, form);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        collectionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
