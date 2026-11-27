package com.example.appShop.services;

import com.example.appShop.dto.CollectionDto;
import com.example.appShop.dto.CollectionFormRequest;
import com.example.appShop.entities.Collection;
import com.example.appShop.exceptions.ConflictException;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.repositories.CollectionRepository;
import com.example.appShop.repositories.TShirtRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Коллекции (дропы) — маркетинговые полки витрины.
 *
 * Два обещания, за которыми мы следим особенно строго:
 *  1) название уникально (без учёта регистра) — иначе на витрине появятся
 *     «Летний дроп» и «летний дроп», и фильтр по коллекции сломается;
 *  2) удаление коллекции НЕ удаляет товары: их можно продавать дальше, и уже
 *     оформленные заказы не должны «ломаться» из-за снятой полки.
 */
@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private TShirtRepository tShirtRepository;

    @InjectMocks
    private CollectionService collectionService;

    private static Collection collection(long id, String name, boolean active) {
        Collection collection = new Collection();
        collection.setId(id);
        collection.setName(name);
        collection.setDescription("Описание");
        collection.setBadgeColor("#FF6B6B");
        collection.setActive(active);
        collection.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return collection;
    }

    private static CollectionFormRequest form(String name, String color, Boolean active) {
        return new CollectionFormRequest(name, "  описание  ", color, active);
    }

    private static CollectionRepository.CollectionProductsRow countRow(Long collectionId, Long productsCount) {
        return new CollectionRepository.CollectionProductsRow() {
            @Override
            public Long getCollectionId() {
                return collectionId;
            }

            @Override
            public Long getProductsCount() {
                return productsCount;
            }
        };
    }

    @Test
    void duplicateNameIsRejectedRegardlessOfCase() {
        when(collectionRepository.existsByNameIgnoreCase("Летний дроп")).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> collectionService.create(form(" Летний дроп ", "#ff6b6b", true)))
                .withMessageContaining("уже есть");

        verify(collectionRepository, never()).save(any());
    }

    @Test
    void createCleansNameDescriptionAndColor() {
        when(collectionRepository.existsByNameIgnoreCase("Summer")).thenReturn(false);
        when(collectionRepository.save(any())).thenAnswer(invocation -> {
            Collection saved = invocation.getArgument(0);
            saved.setId(4L);
            return saved;
        });
        when(tShirtRepository.countOfCollection(4L)).thenReturn(0L);

        CollectionDto dto = collectionService.create(form("   Summer   ", "ff6b6b", null));

        ArgumentCaptor<Collection> saved = ArgumentCaptor.forClass(Collection.class);
        verify(collectionRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Summer");
        assertThat(saved.getValue().getDescription()).isEqualTo("описание");
        // цвет попадёт прямо в CSS фронтенда, поэтому он должен быть каноничным HEX
        assertThat(saved.getValue().getBadgeColor()).isEqualTo("#FF6B6B");
        assertThat(saved.getValue().isActive()).as("по умолчанию коллекция активна").isTrue();

        assertThat(dto.id()).isEqualTo(4L);
        assertThat(dto.productsCount()).isZero();
    }

    @Test
    void renameToAnotherExistingNameIsRejectedButSameNameIsFine() {
        when(collectionRepository.findById(1L)).thenReturn(Optional.of(collection(1L, "Summer", true)));
        when(collectionRepository.existsByNameIgnoreCase("Autumn")).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> collectionService.update(1L, form("Autumn", "#000000", true)));

        // та же самая название (в другом регистре) — не конфликт: иначе правка «ни о чём» падала бы
        when(collectionRepository.findById(1L)).thenReturn(Optional.of(collection(1L, "Summer", true)));
        when(collectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tShirtRepository.countOfCollection(1L)).thenReturn(3L);

        CollectionDto dto = collectionService.update(1L, form("sUmMeR", "#000000", false));

        assertThat(dto.name()).isEqualTo("sUmMeR");
        assertThat(dto.active()).isFalse();
        assertThat(dto.productsCount()).isEqualTo(3L);
    }

    @Test
    void deleteUnlinksProductsInsteadOfDeletingThem() {
        when(collectionRepository.findById(2L)).thenReturn(Optional.of(collection(2L, "Anime", true)));
        when(tShirtRepository.clearCollection(2L)).thenReturn(5);

        collectionService.delete(2L);

        // товары остаются, у них просто пропадает полка; заказы не затрагиваются вовсе
        verify(tShirtRepository).clearCollection(2L);
        verify(tShirtRepository, never()).deleteAll();
        verify(collectionRepository).delete(any(Collection.class));
    }

    @Test
    void deletingUnknownCollectionGivesNotFoundAndTouchesNothing() {
        when(collectionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> collectionService.delete(404L));

        verify(tShirtRepository, never()).clearCollection(org.mockito.ArgumentMatchers.anyLong());
        verify(collectionRepository, never()).delete(any());
    }

    @Test
    void storefrontSeesOnlyActiveCollections() {
        when(collectionRepository.findByActiveTrueOrderByIdDesc())
                .thenReturn(List.of(collection(1L, "Summer", true)));
        when(collectionRepository.countProducts()).thenReturn(List.of(countRow(1L, 12L)));

        List<CollectionDto> active = collectionService.findActive();

        assertThat(active).singleElement().satisfies(dto -> {
            assertThat(dto.name()).isEqualTo("Summer");
            assertThat(dto.productsCount()).isEqualTo(12L);
        });
    }

    @Test
    void productCountsComeFromOneAggregateQueryNotFromQueryPerCollection() {
        when(collectionRepository.findAllByOrderByActiveDescIdDesc())
                .thenReturn(List.of(collection(1L, "Summer", true), collection(2L, "Autumn", false)));
        when(collectionRepository.countProducts()).thenReturn(List.of(countRow(1L, 12L)));

        List<CollectionDto> all = collectionService.findAll();

        // классическая ловушка N+1: «count на каждую коллекцию». Проверяем, что запрос один
        verify(collectionRepository, times(1)).countProducts();
        verify(tShirtRepository, never()).countOfCollection(org.mockito.ArgumentMatchers.anyLong());

        assertThat(all).extracting(CollectionDto::productsCount).containsExactly(12L, 0L);
    }

    @Test
    void productFormWithoutCollectionDoesNotTouchCollectionsAtAll() {
        assertThat(collectionService.resolveOrNull(null)).isNull();
        verify(collectionRepository, never()).findById(any());
    }

    @Test
    void productFormWithUnknownCollectionFailsWithNotFound() {
        when(collectionRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> collectionService.resolveOrNull(9L));
    }

    @Test
    void productFormWithExistingCollectionGetsEntity() {
        when(collectionRepository.findById(3L)).thenReturn(Optional.of(collection(3L, "Oversize", true)));

        assertThat(collectionService.resolveOrNull(3L).getName()).isEqualTo("Oversize");
    }

    @Test
    void emptyAdminListDoesNotQueryProducts() {
        // дополнительная защита от N+1: счётчики берутся списком, а не по id коллекций
        when(collectionRepository.findAllByOrderByActiveDescIdDesc()).thenReturn(List.of());
        when(collectionRepository.countProducts()).thenReturn(List.of());

        assertThat(collectionService.findAll()).isEmpty();
        verify(tShirtRepository, never()).countOfCollection(org.mockito.ArgumentMatchers.anyLong());
        verify(tShirtRepository, never()).findAllById(anyList());
    }
}
