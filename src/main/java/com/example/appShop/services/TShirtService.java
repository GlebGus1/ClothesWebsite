package com.example.appShop.services;

import com.example.appShop.dto.TShirtDetailsDto;
import com.example.appShop.dto.TShirtDto;
import com.example.appShop.dto.TShirtFormRequest;
import com.example.appShop.entities.Image;
import com.example.appShop.entities.TShirt;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.mapper.TShirtMapper;
import com.example.appShop.repositories.ImageRepository;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.projection.ImageIdProjection;
import com.example.appShop.specification.TShirtSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог футболок: поиск с фильтрами, карточка, CRUD для админа, картинки.
 *
 * Ключевое отличие от старого TShirtController: фильтры приходят ПАРАМЕТРАМИ запроса,
 * а не хранятся в полях контроллера (currentSize/currentOrder). Контроллер в Spring —
 * синглтон: один объект на всех пользователей, поэтому старая версия «запоминала»
 * фильтр одного пользователя для всех остальных (гонка между запросами).
 */
@Service
public class TShirtService {

    private static final int MAX_PAGE_SIZE = 50;

    private final TShirtRepository tShirtRepository;
    private final ImageRepository imageRepository;
    private final TShirtMapper tShirtMapper;

    public TShirtService(TShirtRepository tShirtRepository,
                         ImageRepository imageRepository,
                         TShirtMapper tShirtMapper) {
        this.tShirtRepository = tShirtRepository;
        this.imageRepository = imageRepository;
        this.tShirtMapper = tShirtMapper;
    }

    @Transactional(readOnly = true)
    public Page<TShirtDto> search(String size, Boolean available, String sort,
                                  int page, int pageSize, boolean includeDisabled) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampPageSize(pageSize),
                TShirtSort.from(sort).toSort());
        Page<TShirt> found = tShirtRepository
                .findAll(TShirtSpecifications.byFilters(size, available, includeDisabled), pageable);

        Map<Long, List<Long>> galleries = imageIdsByProduct(found.getContent());
        return found.map(tShirt -> tShirtMapper.toDto(tShirt, galleries.getOrDefault(tShirt.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public TShirtDetailsDto getDetails(long id) {
        TShirt tShirt = getOrThrow(id);
        return tShirtMapper.toDetailsDto(tShirt, imageRepository.findAllByTShirt(id));
    }

    /**
     * Карточка для админки. Публичный getDetails скрывает снятые с продажи
     * (is_disabled=true) товары, и админ не мог открыть форму правки такого товара —
     * получал 404. Этот метод читает ЛЮБУЮ позицию, включая скрытую.
     */
    @Transactional(readOnly = true)
    public TShirtDetailsDto getDetailsForAdmin(long id) {
        TShirt tShirt = getForAdminOrThrow(id);
        return tShirtMapper.toDetailsDto(tShirt, imageRepository.findAllByTShirt(id));
    }

    /** Карточки «избранного» и других списков сущностей: одним запросом тянем id галерей. */
    @Transactional(readOnly = true)
    public List<TShirtDto> asDtoList(List<TShirt> tShirts) {
        Map<Long, List<Long>> galleries = imageIdsByProduct(tShirts);
        return tShirts.stream()
                .map(tShirt -> tShirtMapper.toDto(tShirt, galleries.getOrDefault(tShirt.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> availableSizes() {
        return tShirtRepository.findDistinctSizes();
    }

    @Transactional
    public TShirtDetailsDto create(TShirtFormRequest form, List<MultipartFile> files) {
        ImageUploadPolicy.checkCount(files);

        TShirt tShirt = new TShirt();
        applyForm(tShirt, form);
        if (tShirt.getDateOfCreated() == null) {
            // сортировка «сначала новые» работает по этой колонке — заполняем при создании
            tShirt.setDateOfCreated(java.time.LocalDateTime.now());
        }
        TShirt saved = tShirtRepository.save(tShirt);

        List<Image> images = attachImages(saved, files);
        fixPreview(saved, images);
        tShirtRepository.save(saved);

        return tShirtMapper.toDetailsDto(saved, images);
    }

    /**
     * Обновление товара: поля меняем всегда, картинки только ДОБАВЛЯЕМ.
     * Удаляются картинки отдельной операцией DELETE /api/images/{id}.
     *
     * Скрытый (is_disabled=true) товар тоже можно редактировать: PUT /api/tshirts/{id}
     * доступен только роли ADMIN (SecurityConfig), поэтому здесь читаем без фильтра.
     */
    @Transactional
    public TShirtDetailsDto update(long id, TShirtFormRequest form, List<MultipartFile> files) {
        ImageUploadPolicy.checkCount(files);

        TShirt tShirt = getForAdminOrThrow(id);
        applyForm(tShirt, form);

        attachImages(tShirt, files);
        fixPreview(tShirt, imageRepository.findAllByTShirt(id));
        tShirtRepository.save(tShirt);

        return tShirtMapper.toDetailsDto(tShirt, imageRepository.findAllByTShirt(id));
    }

    /**
     * «Вернуть в продажу» для скрытого товара: is_disabled=false + available=true.
     * Оба поля ставим вместе, потому что delete() при скрытии выключает их парой
     * (см. метод delete) — иначе товар вернулся бы в каталог с плашкой «продано».
     */
    @Transactional
    public TShirtDetailsDto restore(long id) {
        TShirt tShirt = getForAdminOrThrow(id);
        tShirt.setDisabled(false);
        tShirt.setAvailable(true);
        tShirtRepository.save(tShirt);
        return getDetailsForAdmin(id);
    }

    /**
     * Удаление товара делаем МЯГКИМ (is_disabled = true): на товар уже могут ссылаться
     * заказы (внешний ключ в order_tshirts), поэтому жёсткое DELETE из MySQL упало бы
     * ошибкой целостности, а заказ остался бы без позиции. Отключённый товар скрыт
     * из каталога, карточки и фильтра, картинки при этом остаются привязанными.
     */
    @Transactional
    public void delete(long id) {
        TShirt tShirt = getOrThrow(id);
        tShirt.setDisabled(true);
        tShirt.setAvailable(false);
        tShirtRepository.save(tShirt);
    }

    @Transactional
    public void deleteImage(long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Картинка", imageId));
        TShirt tShirt = image.gettShirt();
        imageRepository.delete(image);
        imageRepository.flush();

        if (tShirt != null) {
            fixPreview(tShirt, imageRepository.findAllByTShirt(tShirt.getId()));
            tShirtRepository.save(tShirt);
        }
    }

    @Transactional
    public TShirtDetailsDto setPreviewImage(long tShirtId, long imageId) {
        TShirt tShirt = getForAdminOrThrow(tShirtId);
        imageRepository.findById(imageId)
                .filter(image -> image.gettShirt() != null && image.gettShirt().getId() == tShirtId)
                .orElseThrow(() -> new NotFoundException("Картинка", imageId));

        tShirt.setPreviewImageId(Math.toIntExact(imageId));
        tShirtRepository.save(tShirt);
        return getDetailsForAdmin(tShirtId);
    }

    private TShirt getOrThrow(long id) {
        return tShirtRepository.findById(id)
                .filter(tShirt -> !tShirt.isDisabled())
                .orElseThrow(() -> new NotFoundException("Футболка", id));
    }

    /** Чтение без фильтра «не скрыт»: только для операций, доступных админу. */
    private TShirt getForAdminOrThrow(long id) {
        return tShirtRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Футболка", id));
    }

    /**
     * Id всех картинок страницы каталога одним запросом (а не «по запросу на карточку»)
     * и без longblob: проекция ImageIdProjection тянет из базы только числа.
     * Внутри списка главное фото ставим первым — карточка открывает листание с обложки.
     */
    private Map<Long, List<Long>> imageIdsByProduct(List<TShirt> tShirts) {
        if (tShirts.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = tShirts.stream().map(TShirt::getId).toList();

        Map<Long, List<Long>> imageIds = new HashMap<>();
        for (ImageIdProjection row : imageRepository.findImageIdsOfProducts(productIds)) {
            imageIds.computeIfAbsent(row.getProductId(), id -> new ArrayList<>()).add(row.getImageId());
        }

        Map<Long, List<Long>> ordered = new LinkedHashMap<>();
        for (TShirt tShirt : tShirts) {
            List<Long> ids = imageIds.get(tShirt.getId());
            if (ids != null) {
                ordered.put(tShirt.getId(), withPreviewFirst(ids, tShirt.getPreviewImageId()));
            }
        }
        return ordered;
    }

    private List<Long> withPreviewFirst(List<Long> ids, int previewImageId) {
        int index = ids.indexOf((long) previewImageId);
        if (index <= 0) {
            return List.copyOf(ids);
        }
        List<Long> reordered = new ArrayList<>(ids);
        reordered.add(0, reordered.remove(index));
        return List.copyOf(reordered);
    }

    private void applyForm(TShirt tShirt, TShirtFormRequest form) {
        tShirt.setName(form.name().trim());
        tShirt.setDescription(form.description());
        tShirt.setSize(form.size().trim().toUpperCase());
        tShirt.setPrice(form.price());
        tShirt.setAvailable(form.isAvailable());
    }

    private List<Image> attachImages(TShirt tShirt, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<Image> attached = files.stream()
                .filter(file -> !ImageUploadPolicy.isEmpty(file))
                .map(ImageUploadPolicy::toImageEntity)
                .toList();
        attached.forEach(tShirt::addImageToTShirt);
        // Сохраняем картинки сразу и отдельно: только так мы получаем их id (их выдаёт MySQL),
        // а id нужны и для корректного превью, и для ответа клиенту.
        imageRepository.saveAll(attached);
        imageRepository.flush();
        return attached;
    }

    /** Превью должно быть обязательно валидным, иначе каталог покажет битую картинку. */
    private void fixPreview(TShirt tShirt, List<Image> images) {
        if (images.isEmpty()) {
            return;
        }
        boolean previewExists = images.stream()
                .anyMatch(image -> image.getId() != null && image.getId() == tShirt.getPreviewImageId());
        if (!previewExists) {
            tShirt.setPreviewImageId(Math.toIntExact(images.get(0).getId()));
        }
    }

    private int clampPageSize(int pageSize) {
        if (pageSize <= 0) {
            return 12;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
