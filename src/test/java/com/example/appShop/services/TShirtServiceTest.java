package com.example.appShop.services;

import com.example.appShop.dto.ImageDto;
import com.example.appShop.dto.TShirtDetailsDto;
import com.example.appShop.dto.TShirtDto;
import com.example.appShop.dto.TShirtFormRequest;
import com.example.appShop.entities.Image;
import com.example.appShop.entities.TShirt;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.mapper.TShirtMapper;
import com.example.appShop.repositories.ImageRepository;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.projection.ImageIdProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Каталог футболок. Здесь проверяем «неочевидное»: сортировку по белому списку,
 * мягкое удаление, превью и правила загрузки файлов.
 */
@ExtendWith(MockitoExtension.class)
class TShirtServiceTest {

    @Mock
    private TShirtRepository tShirtRepository;
    @Mock
    private ImageRepository imageRepository;
    @Spy
    private TShirtMapper tShirtMapper = new TShirtMapper();

    @InjectMocks
    private TShirtService tShirtService;

    private static TShirt shirt(long id, boolean disabled) {
        TShirt tShirt = new TShirt();
        tShirt.setId(id);
        tShirt.setName("Футболка");
        tShirt.setSize("M");
        tShirt.setPrice(100.0);
        tShirt.setAvailable(true);
        tShirt.setDisabled(disabled);
        tShirt.setPreviewImageId(0);
        return tShirt;
    }

    private static TShirtFormRequest form() {
        return new TShirtFormRequest("  Новая футболка ", "описание", " m ", 250.0, true);
    }

    /** Настоящий PNG-заголовок + «хвост»: политика проверяет содержимое, а не только MIME. */
    private static final byte[] PNG_BYTES =
            {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    private static MultipartFile png(String name) {
        return new MockMultipartFile("files", name, "image/png", PNG_BYTES);
    }

    @Test
    void searchUsesDefaultSortAndPageSize() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        tShirtService.search(null, null, null, 0, 0, false);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tShirtRepository).findAll(any(Specification.class), pageable.capture());
        Sort.Order order = pageable.getValue().getSort().getOrderFor("dateOfCreated");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
    }

    @Test
    void unknownSortFallsBackToDateOfCreated() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        tShirtService.search(null, null, "price; drop table tshirts", 1, 1000, false);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tShirtRepository).findAll(any(Specification.class), pageable.capture());
        // клиент не может указать своё поле сортировки — только разрешённый список
        assertThat(pageable.getValue().getSort()).isEqualTo(TShirtSort.NEWEST.toSort());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
    }

    @Test
    void searchReturnsMappedPage() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shirt(1L, false))));

        assertThat(tShirtService.search("M", true, "price_asc", 0, 12, false).getContent())
                .singleElement()
                .satisfies(dto -> assertThat(dto.id()).isEqualTo(1L));
    }

    @Test
    void searchReturnsGalleryIdsWithPreviewFirst() {
        TShirt tShirt = shirt(1L, false);
        tShirt.setPreviewImageId(12);
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tShirt)));
        when(imageRepository.findImageIdsOfProducts(List.of(1L)))
                .thenReturn(List.of(new ImageRow(1L, 10), new ImageRow(1L, 12)));

        TShirtDto dto = tShirtService.search(null, null, null, 0, 12, false).getContent().get(0);

        // карточка в каталоге листает фото прямо в списке: id всех картинок идут в DTO,
        // а обложка — первой, чтобы нулевой слайд совпадал с главным фото
        assertThat(dto.previewImageId()).isEqualTo(12);
        assertThat(dto.imageIds()).containsExactly(12L, 10L);
    }

    @Test
    void emptyCatalogPageDoesNotQueryImagesAtAll() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        tShirtService.search(null, null, null, 0, 12, false);

        // «where id in ()» база не понимает, поэтому лишний запрос не отправляем
        verify(imageRepository, never()).findImageIdsOfProducts(anyList());
    }

    @Test
    void cardMarksExactlyOneImageAsPreview() {
        TShirt tShirt = shirt(9L, false);
        tShirt.setPreviewImageId(55);
        when(tShirtRepository.findById(9L)).thenReturn(Optional.of(tShirt));
        when(imageRepository.findAllByTShirt(9L)).thenReturn(List.of(image(50L, tShirt), image(55L, tShirt)));

        TShirtDetailsDto dto = tShirtService.getDetails(9L);

        // флаг «главное фото» считаем из tshirts.preview_image_id, а не из устаревшей колонки
        assertThat(dto.images()).filteredOn(ImageDto::previewImage)
                .extracting(ImageDto::id).containsExactly(55L);
    }

    @Test
    void hiddenProductIsNotShownInCard() {
        when(tShirtRepository.findById(5L)).thenReturn(Optional.of(shirt(5L, true)));

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> tShirtService.getDetails(5L));
    }

    @Test
    void createAssignsPreviewAndImageIds() {
        when(tShirtRepository.save(any(TShirt.class))).thenAnswer(inv -> {
            TShirt tShirt = inv.getArgument(0);
            if (tShirt.getId() == null) {
                tShirt.setId(42L);
            }
            return tShirt;
        });
        AtomicLong sequence = new AtomicLong(100);
        when(imageRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Image> images = inv.getArgument(0);
            images.forEach(image -> image.setId(sequence.incrementAndGet()));
            return images;
        });

        TShirtDetailsDto dto = tShirtService.create(form(), List.of(png("a.png"), png("b.png")));

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.images()).hasSize(2);
        assertThat(dto.previewImageId()).isEqualTo(101);
        assertThat(dto.dateOfCreated()).isNotNull();
        verify(imageRepository).flush();
    }

    @Test
    void createNormalizesSizeAndName() {
        when(tShirtRepository.save(any(TShirt.class))).thenAnswer(inv -> {
            TShirt saved = inv.getArgument(0);
            saved.setId(43L);
            return saved;
        });

        TShirtDetailsDto dto = tShirtService.create(form(), List.of());

        assertThat(dto.name()).isEqualTo("Новая футболка");
        assertThat(dto.size()).isEqualTo("M");
        assertThat(dto.images()).isEmpty();
    }

    @Test
    void createRefusesTooManyFiles() {
        List<MultipartFile> files = IntStream.rangeClosed(1, 6).mapToObj(i -> png("f" + i + ".png")).toList();

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> tShirtService.create(form(), files))
                .withMessageContaining("не больше 5");
    }

    @Test
    void createRefusesTooBigFile() {
        byte[] big = new byte[(int) ImageUploadPolicy.MAX_FILE_SIZE_BYTES + 1];
        MultipartFile file = new MockMultipartFile("files", "big.png", "image/png", big);

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> tShirtService.create(form(), List.of(file)))
                .withMessageContaining("больше 5 МБ");
    }

    @Test
    void createRefusesExecutableDisguisedAsImage() {
        MultipartFile file = new MockMultipartFile("files", "shell.exe", "application/x-msdownload",
                new byte[]{1, 2, 3});

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> tShirtService.create(form(), List.of(file)))
                .withMessageContaining("Разрешены только изображения");
    }

    @Test
    void createRefusesFileThatOnlyClaimsToBePng() {
        // MIME «image/png» рисует браузер, поэтому смотрим на первые байты файла
        MultipartFile fake = new MockMultipartFile("files", "payload.png", "image/png",
                new byte[]{'M', 'Z', (byte) 0x90, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10});

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> tShirtService.create(form(), List.of(fake)))
                .withMessageContaining("не похож на изображение");
    }

    @Test
    void updateKeepsExistingPreview() {
        TShirt existing = shirt(7L, false);
        existing.setPreviewImageId(55);
        when(tShirtRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(imageRepository.findAllByTShirt(7L)).thenReturn(List.of(image(55L, existing)));

        TShirtDetailsDto dto = tShirtService.update(7L, form(), List.of());

        assertThat(dto.previewImageId()).isEqualTo(55);
        assertThat(existing.getPreviewImageId()).isEqualTo(55);
    }

    @Test
    void deleteIsSoftAndKeepsOrderHistory() {
        TShirt existing = shirt(7L, false);
        when(tShirtRepository.findById(7L)).thenReturn(Optional.of(existing));

        tShirtService.delete(7L);

        assertThat(existing.isDisabled()).isTrue();
        assertThat(existing.isAvailable()).isFalse();
        // жёсткое DELETE сломало бы внешний ключ в order_tshirts
        verify(tShirtRepository, never()).delete(any(TShirt.class));
    }

    @Test
    void deletingUnknownProductGivesNotFound() {
        when(tShirtRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> tShirtService.delete(404L));
    }

    @Test
    void deleteImageRepointsPreviewToRemainingImage() {
        TShirt tShirt = shirt(7L, false);
        tShirt.setPreviewImageId(55);
        Image old = image(55L, tShirt);
        Image remaining = image(60L, tShirt);
        when(imageRepository.findById(55L)).thenReturn(Optional.of(old));
        when(imageRepository.findAllByTShirt(7L)).thenReturn(List.of(remaining));

        tShirtService.deleteImage(55L);

        assertThat(tShirt.getPreviewImageId()).isEqualTo(60);
        verify(imageRepository).delete(old);
    }

    @Test
    void setPreviewRejectsImageOfAnotherProduct() {
        TShirt tShirt = shirt(7L, false);
        TShirt other = shirt(8L, false);
        when(tShirtRepository.findById(7L)).thenReturn(Optional.of(tShirt));
        when(imageRepository.findById(55L)).thenReturn(Optional.of(image(55L, other)));

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> tShirtService.setPreviewImage(7L, 55L));
    }

    @Test
    void setPreviewChangesPreviewId() {
        TShirt tShirt = shirt(7L, false);
        when(tShirtRepository.findById(7L)).thenReturn(Optional.of(tShirt));
        when(imageRepository.findById(55L)).thenReturn(Optional.of(image(55L, tShirt)));
        when(imageRepository.findAllByTShirt(7L)).thenReturn(List.of(image(55L, tShirt)));

        tShirtService.setPreviewImage(7L, 55L);

        assertThat(tShirt.getPreviewImageId()).isEqualTo(55);
    }

    @Test
    void setPreviewReturnsGalleryWithNewCoverHighlighted() {
        TShirt tShirt = shirt(7L, false);
        tShirt.setPreviewImageId(50);
        when(tShirtRepository.findById(7L)).thenReturn(Optional.of(tShirt));
        when(imageRepository.findById(55L)).thenReturn(Optional.of(image(55L, tShirt)));
        when(imageRepository.findAllByTShirt(7L)).thenReturn(List.of(image(50L, tShirt), image(55L, tShirt)));

        TShirtDetailsDto dto = tShirtService.setPreviewImage(7L, 55L);

        assertThat(dto.previewImageId()).isEqualTo(55);
        assertThat(dto.images()).filteredOn(ImageDto::previewImage)
                .extracting(ImageDto::id).containsExactly(55L);
    }

    /** Тестовая замена проекции ImageIdProjection (Spring Data собирает её из запроса). */
    private record ImageRow(long productId, long imageId) implements ImageIdProjection {

        @Override
        public long getProductId() {
            return productId;
        }

        @Override
        public long getImageId() {
            return imageId;
        }
    }

    private static Image image(long id, TShirt tShirt) {
        Image image = new Image();
        image.setId(id);
        image.setName("a.png");
        image.setOriginalFileName("a.png");
        image.setContentType("image/png");
        image.setSize(4L);
        image.setBytes(new byte[]{1, 2, 3, 4});
        image.settShirt(tShirt);
        return image;
    }
}
