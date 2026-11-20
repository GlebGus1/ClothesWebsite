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
import com.example.appShop.specification.TShirtSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
        return tShirtRepository
                .findAll(TShirtSpecifications.byFilters(size, available, includeDisabled), pageable)
                .map(tShirtMapper::toDto);
    }

    @Transactional(readOnly = true)
    public TShirtDetailsDto getDetails(long id) {
        TShirt tShirt = getOrThrow(id);
        return tShirtMapper.toDetailsDto(tShirt, imageRepository.findAllByTShirt(id));
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
     */
    @Transactional
    public TShirtDetailsDto update(long id, TShirtFormRequest form, List<MultipartFile> files) {
        ImageUploadPolicy.checkCount(files);

        TShirt tShirt = getOrThrow(id);
        applyForm(tShirt, form);

        attachImages(tShirt, files);
        fixPreview(tShirt, imageRepository.findAllByTShirt(id));
        tShirtRepository.save(tShirt);

        return tShirtMapper.toDetailsDto(tShirt, imageRepository.findAllByTShirt(id));
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
        TShirt tShirt = getOrThrow(tShirtId);
        imageRepository.findById(imageId)
                .filter(image -> image.gettShirt() != null && image.gettShirt().getId() == tShirtId)
                .orElseThrow(() -> new NotFoundException("Картинка", imageId));

        tShirt.setPreviewImageId(Math.toIntExact(imageId));
        tShirtRepository.save(tShirt);
        return getDetails(tShirtId);
    }

    private TShirt getOrThrow(long id) {
        return tShirtRepository.findById(id)
                .filter(tShirt -> !tShirt.isDisabled())
                .orElseThrow(() -> new NotFoundException("Футболка", id));
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
