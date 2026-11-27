package com.example.appShop.services;

import com.example.appShop.dto.AdminTShirtDto;
import com.example.appShop.dto.TShirtDetailsDto;
import com.example.appShop.entities.TShirt;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.mapper.TShirtMapper;
import com.example.appShop.repositories.ImageRepository;
import com.example.appShop.repositories.OrderRepository;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.projection.TshirtOrderRow;
import com.example.appShop.specification.TShirtSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Таблица «Товары» в админке.
 *
 * Почему это отдельный сервис, а ещё один метод в TShirtService:
 *  - публичному каталогу нельзя знать про заказы (это данные другого пользователя),
 *    а админской таблице нужно;
 *  - в TShirtService и так каталог + картинки + CRUD, и добавлять туда OrderRepository
 *    означало бы смешать слои и раздуть файл.
 *
 * Что здесь «админского»:
 *  1) viewsCount — счётчик просмотров (для сортировки и понимания, что реально смотрят);
 *  2) soldInOrderId — в каком заказе ушёл товар, чтобы в таблице показать кликабельный
 *     бейдж «Заказ #7» и одним шагом уходить в этот заказ (а из заказа — обратно в товар).
 *     Все связи узнаём ОДНИМ запросом (findOrdersOfProducts) на всю страницу,
 *     а не «запрос на строку таблицы».
 */
@Service
public class AdminProductCatalogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TShirtRepository tShirtRepository;
    private final OrderRepository orderRepository;
    private final ImageRepository imageRepository;
    private final TShirtMapper tShirtMapper;

    public AdminProductCatalogService(TShirtRepository tShirtRepository,
                                      OrderRepository orderRepository,
                                      ImageRepository imageRepository,
                                      TShirtMapper tShirtMapper) {
        this.tShirtRepository = tShirtRepository;
        this.orderRepository = orderRepository;
        this.imageRepository = imageRepository;
        this.tShirtMapper = tShirtMapper;
    }

    @Transactional(readOnly = true)
    public Page<AdminTShirtDto> search(String size, Boolean available, Long collectionId, String sort,
                                       int page, int pageSize, boolean includeDisabled) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampPageSize(pageSize),
                TShirtSort.from(sort).toSort());
        Page<TShirt> found = tShirtRepository.findAll(
                TShirtSpecifications.byFilters(size, available, collectionId, includeDisabled), pageable);

        Map<Long, Long> orderIds = lastOrderIdByProduct(found.getContent());
        return found.map(tShirt -> toAdminDto(tShirt, orderIds.get(tShirt.getId())));
    }

    /**
     * Быстрая смена «в продаже / нет в продаже» прямо из таблицы.
     * Отдельная короткая PATCH-операция, а не «открыть форму, снять галочку, сохранить
     * форму»: так щелчок не сбивает незаполненные поля товара и не требует правки всего.
     */
    @Transactional
    public TShirtDetailsDto setAvailability(long id, boolean available) {
        TShirt tShirt = tShirtRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Футболка", id));
        tShirt.setAvailable(available);
        tShirtRepository.save(tShirt);
        // Возвращаем полный DTO, чтобы фронтенд перекрасил строку и бейдж «нет в продаже»
        // без перезагрузки всей страницы.
        return tShirtMapper.toDetailsDto(tShirt, imageRepository.findAllByTShirt(id));
    }

    private Map<Long, Long> lastOrderIdByProduct(List<TShirt> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = products.stream().map(TShirt::getId).toList();
        Map<Long, Long> latest = new HashMap<>();
        for (TshirtOrderRow row : orderRepository.findOrdersOfProducts(ids)) {
            // один товар мог уходить в разные заказы (например, «вернули в продажу» и продали снова)
            latest.merge(row.getTshirtId(), row.getOrderId(), Math::max);
        }
        return latest;
    }

    private AdminTShirtDto toAdminDto(TShirt tShirt, Long soldInOrderId) {
        Integer preview = tShirt.getPreviewImageId() > 0 ? tShirt.getPreviewImageId() : null;
        return new AdminTShirtDto(
                tShirt.getId(),
                tShirt.getName(),
                tShirt.getSize(),
                tShirt.getPrice(),
                tShirt.isAvailable(),
                tShirt.isDisabled(),
                tShirt.getDateOfCreated(),
                preview,
                tShirt.getViewsCount(),
                tShirt.getCollection() == null ? null : tShirt.getCollection().getId(),
                tShirt.getCollection() == null ? null : tShirt.getCollection().getName(),
                soldInOrderId);
    }

    private int clampPageSize(int pageSize) {
        if (pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
