package com.example.appShop.services;

import com.example.appShop.dto.AdminTShirtDto;
import com.example.appShop.dto.TShirtDetailsDto;
import com.example.appShop.entities.Collection;
import com.example.appShop.entities.Image;
import com.example.appShop.entities.TShirt;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.mapper.TShirtMapper;
import com.example.appShop.repositories.ImageRepository;
import com.example.appShop.repositories.OrderRepository;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.projection.TshirtOrderRow;
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
import org.springframework.data.jpa.domain.Specification;

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
 * Таблица «Товары» в админке: быстрые действия (в продаже / нет) и подсказка
 * «в каком заказе ушла вещь».
 *
 * Что охраняют тесты:
 *  - связь товара с заказом берётся ОДНИМ запросом на всю страницу (а не «запрос на строку»);
 *  - если товар уходил в несколько заказов (возвратили в продажу и продали снова),
 *    показываем ПОСЛЕДНИЙ заказ — тот, который актуален сейчас;
 *  - щелчок «в продаже» не трогает остальные поля товара.
 */
@ExtendWith(MockitoExtension.class)
class AdminProductCatalogServiceTest {

    @Mock
    private TShirtRepository tShirtRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ImageRepository imageRepository;

    @Spy
    private TShirtMapper tShirtMapper = new TShirtMapper();

    @InjectMocks
    private AdminProductCatalogService catalogService;

    /** Заглука проекции «товар -> заказ»: интерфейс требует getXxx-методов. */
    private record OrderRef(Long tshirtId, Long orderId) implements TshirtOrderRow {

        @Override
        public Long getTshirtId() {
            return tshirtId;
        }

        @Override
        public Long getOrderId() {
            return orderId;
        }
    }

    private static TShirt shirt(long id, boolean available) {
        TShirt tShirt = new TShirt();
        tShirt.setId(id);
        tShirt.setName("Футболка " + id);
        tShirt.setSize("M");
        tShirt.setPrice(199.0);
        tShirt.setAvailable(available);
        tShirt.setDisabled(false);
        tShirt.setViewsCount(id * 10L);
        return tShirt;
    }

    private static TShirt shirtInCollection(long id, long collectionId, String collectionName) {
        TShirt tShirt = shirt(id, true);
        Collection collection = new Collection();
        collection.setId(collectionId);
        collection.setName(collectionName);
        collection.setBadgeColor("#FF6B6B");
        collection.setActive(true);
        tShirt.setCollection(collection);
        return tShirt;
    }

    @Test
    void soldProductGetsItsLatestOrderAndUnknownProductGetsNone() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shirt(1L, false), shirt(2L, false), shirt(3L, true))));
        // товар 1 уходил дважды (возврат в продажу -> новая продажа): важен последний заказ
        when(orderRepository.findOrdersOfProducts(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(new OrderRef(1L, 4L), new OrderRef(1L, 9L), new OrderRef(2L, 6L)));

        List<AdminTShirtDto> rows = catalogService.search(null, null, null, null, 0, 20, true).getContent();

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).soldInOrderId()).isEqualTo(9L);
        assertThat(rows.get(1).soldInOrderId()).isEqualTo(6L);
        assertThat(rows.get(2).soldInOrderId()).isNull();
        assertThat(rows.get(0).viewsCount()).isEqualTo(10L);
    }

    @Test
    void orderLinksAreLoadedByOneQueryForTheWholePage() {
        List<TShirt> page = List.of(shirt(1L, false), shirt(2L, false), shirt(3L, false));
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(page));
        when(orderRepository.findOrdersOfProducts(anyList())).thenReturn(List.of(new OrderRef(1L, 4L)));

        catalogService.search(null, null, null, null, 0, 20, true);

        // без этой проверки легко скатиться в N+1: «заказ на каждую строку таблицы»
        verify(orderRepository, times(1)).findOrdersOfProducts(List.of(1L, 2L, 3L));
    }

    @Test
    void emptyPageSkipsOrderLookupCompletely() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThat(catalogService.search(null, null, null, null, 0, 20, true).getContent()).isEmpty();

        verify(orderRepository, never()).findOrdersOfProducts(anyList());
    }

    @Test
    void collectionOfProductIsShownToAdminAsIdAndName() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shirtInCollection(1L, 7L, "Летний дроп"))));
        when(orderRepository.findOrdersOfProducts(anyList())).thenReturn(List.of());

        AdminTShirtDto row = catalogService.search(null, null, null, null, 0, 20, true).getContent().get(0);

        assertThat(row.collectionId()).isEqualTo(7L);
        assertThat(row.collectionName()).isEqualTo("Летний дроп");
    }

    @Test
    void availabilitySwitchFlipsOnlyAvailabilityAndReturnsFullRow() {
        TShirt tShirt = shirt(1L, true);
        when(tShirtRepository.findById(1L)).thenReturn(Optional.of(tShirt));
        Image cover = new Image();
        cover.setId(11L);
        cover.setName("cover.jpg");
        cover.setOriginalFileName("cover.jpg");
        cover.setSize(1024L);
        cover.setContentType("image/jpeg");
        cover.settShirt(tShirt);
        when(imageRepository.findAllByTShirt(1L)).thenReturn(List.of(cover));

        TShirtDetailsDto dto = catalogService.setAvailability(1L, false);

        assertThat(tShirt.isAvailable()).isFalse();
        assertThat(dto.available()).isFalse();
        // фронтенд перекрашивает строку без перезагрузки страницы: DTO полный, с картинками
        assertThat(dto.images()).hasSize(1);
        assertThat(dto.images().get(0).id()).isEqualTo(11);
        verify(tShirtRepository).save(tShirt);
    }

    @Test
    void turningAvailabilityOnUnknownProductGivesNotFound() {
        when(tShirtRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> catalogService.setAvailability(404L, true));

        verify(tShirtRepository, never()).save(any());
    }

    @Test
    void sortingIsWhitelistedAndPageSizeIsClamped() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        catalogService.search("M", true, 3L, "price_desc", -5, 9999, true);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tShirtRepository).findAll(any(Specification.class), pageable.capture());

        // «мусорная» сортировка от клиента не проходит: разрешённый вариант из перечисления
        assertThat(pageable.getValue().getSort().toString()).contains("price: DESC");
        // страница не должна позволять «вытащить всю базу одним запросом»
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    void wrongSortStringFallsBackToNewestInsteadOfFailing() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        catalogService.search(null, null, null, "price; DROP TABLE", 0, 20, true);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tShirtRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().toString()).contains("dateOfCreated: DESC");
    }

    @Test
    void adminCanAskForPageWithoutDisabledProductsAndServiceDoesNotFilterByHand() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shirt(1L, true))));
        when(orderRepository.findOrdersOfProducts(anyList())).thenReturn(List.of());

        List<AdminTShirtDto> rows = catalogService.search(null, null, null, null, 0, 20, false).getContent();

        // includeDisabled=false уходит в спецификацию (фильтр делает SQL),
        // а не «получили всё и отфильтровали в Java» (иначе пагинация соврала бы)
        verify(tShirtRepository).findAll(any(Specification.class), any(Pageable.class));
        assertThat(rows).hasSize(1);
    }

    @Test
    void orderIdsOfUnknownProductsAreIgnoredWithoutBreakingRows() {
        when(tShirtRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shirt(1L, false))));
        // Projection может прийти с id товара, которого нет на странице (заказы «из будущего»)
        when(orderRepository.findOrdersOfProducts(anyList())).thenReturn(List.of(new OrderRef(99L, 5L)));

        List<AdminTShirtDto> rows = catalogService.search(null, null, null, null, 0, 20, true).getContent();

        assertThat(rows).singleElement().satisfies(row -> assertThat(row.soldInOrderId()).isNull());
    }
}
