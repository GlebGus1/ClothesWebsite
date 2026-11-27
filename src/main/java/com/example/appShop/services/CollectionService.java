package com.example.appShop.services;

import com.example.appShop.dto.CollectionDto;
import com.example.appShop.dto.CollectionFormRequest;
import com.example.appShop.entities.Collection;
import com.example.appShop.exceptions.ConflictException;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.repositories.CollectionRepository;
import com.example.appShop.repositories.TShirtRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Коллекции (дропы) футболок: «Летний дроп», «Anime», «Оверсайз».
 *
 * Считаем, что коллекция — это маркетинговая полка, а не сущность, от которой зависит
 * жизнь товара:
 *  - товар без коллекции жить обязан (collection = null);
 *  - при удалении коллекции мы ОТВЯЗЫВАЕМ товары (set null), а не удаляем их:
 *    на них уже есть заказы, и удалять позицию заказа из-за снятой полки — зло.
 *
 * Сколько товаров в коллекции, считаем одним запросом на весь список (group by),
 * а не «count на каждую коллекцию» (классическая N+1 ловушка).
 */
@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);

    private final CollectionRepository collectionRepository;
    private final TShirtRepository tShirtRepository;

    public CollectionService(CollectionRepository collectionRepository, TShirtRepository tShirtRepository) {
        this.collectionRepository = collectionRepository;
        this.tShirtRepository = tShirtRepository;
    }

    /** Витрина: только активные коллекции (баннер и чипсы на каталоге). */
    @Transactional(readOnly = true)
    public List<CollectionDto> findActive() {
        return toDtos(collectionRepository.findByActiveTrueOrderByIdDesc());
    }

    /** Админка: все коллекции, активные первыми, с счётчиком товаров. */
    @Transactional(readOnly = true)
    public List<CollectionDto> findAll() {
        return toDtos(collectionRepository.findAllByOrderByActiveDescIdDesc());
    }

    @Transactional
    public CollectionDto create(CollectionFormRequest form) {
        String name = form.name().trim();
        if (collectionRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Коллекция с таким названием уже есть: " + name);
        }
        Collection collection = new Collection();
        apply(collection, form, name);
        return toDto(collectionRepository.save(collection), countOf(collection.getId()));
    }

    @Transactional
    public CollectionDto update(long id, CollectionFormRequest form) {
        Collection collection = getOrThrow(id);
        String name = form.name().trim();
        boolean sameName = collection.getName().equalsIgnoreCase(name);
        if (!sameName && collectionRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Коллекция с таким названием уже есть: " + name);
        }
        apply(collection, form, name);
        Collection saved = collectionRepository.save(collection);
        return toDto(saved, countOf(saved.getId()));
    }

    /**
     * Удаление коллекции. Товары остаются на месте — у них просто пропадает полка
     * (collection_id = null). Заказы при этом не страдают вовсе.
     */
    @Transactional
    public void delete(long id) {
        Collection collection = getOrThrow(id);
        int unlinked = tShirtRepository.clearCollection(id);
        collectionRepository.delete(collection);
        log.info("Удалена коллекция id={} ({} товаров отвязаны)", id, unlinked);
    }

    /** id коллекции по названию (для импорта/отладки); без названия — 404. */
    @Transactional(readOnly = true)
    public Collection getOrThrow(long id) {
        return collectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Коллекция", id));
    }

    /**
     * Используется сервисом товаров: проверить, что админ прислал существующую коллекцию.
     * null возвращаем, когда клиент поля не трогал.
     */
    @Transactional(readOnly = true)
    public Collection resolveOrNull(Long collectionId) {
        if (collectionId == null) {
            return null;
        }
        return getOrThrow(collectionId);
    }

    private void apply(Collection collection, CollectionFormRequest form, String name) {
        collection.setName(name);
        collection.setDescription(form.description() == null ? null : form.description().trim());
        collection.setBadgeColor(form.normalizedBadgeColor());
        collection.setActive(form.isActiveOrDefault());
    }

    private long countOf(long collectionId) {
        return tShirtRepository.countOfCollection(collectionId);
    }

    private List<CollectionDto> toDtos(List<Collection> collections) {
        Map<Long, Long> counts = new HashMap<>();
        for (CollectionRepository.CollectionProductsRow row : collectionRepository.countProducts()) {
            counts.put(row.getCollectionId(), row.getProductsCount());
        }
        return collections.stream()
                .map(collection -> toDto(collection, counts.getOrDefault(collection.getId(), 0L)))
                .toList();
    }

    private CollectionDto toDto(Collection collection, long productsCount) {
        return new CollectionDto(
                collection.getId(),
                collection.getName(),
                collection.getDescription(),
                collection.getBadgeColor(),
                collection.isActive(),
                collection.getCreatedAt(),
                productsCount);
    }
}
