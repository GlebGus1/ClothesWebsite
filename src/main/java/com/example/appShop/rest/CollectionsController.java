package com.example.appShop.rest;

import com.example.appShop.dto.CollectionDto;
import com.example.appShop.services.CollectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Публичный список коллекций (дропов) для витрины: компактный баннер над каталогом
 * и чипсы-переключатели. Гостям показываются ТОЛЬКО АКТИВНЫЕ коллекции — выключенные
 * администратором полки не должны «всплывать» ни в меню, ни в бейджах карточек.
 *
 * Почему здесь нет параметра «?all=true»: соблазн «подмешать админские данные в
 * публичный метод» обычно заканчивается ветвлением по роли в самом чувствительном
 * эндпоинте. Админке нужен полный список — для этого есть /api/admin/collections,
 * весь путь закрыт ролью ADMIN в SecurityConfig.
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionsController {

    private final CollectionService collectionService;

    public CollectionsController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @GetMapping
    public List<CollectionDto> list() {
        return collectionService.findActive();
    }
}
