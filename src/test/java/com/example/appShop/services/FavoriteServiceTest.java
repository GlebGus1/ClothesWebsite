package com.example.appShop.services;

import com.example.appShop.dto.TShirtDto;
import com.example.appShop.entities.TShirt;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.UserRepository;
import com.example.appShop.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Избранное. Проверяем главное: владелец всегда берётся из токена, связь живёт
 * в таблице-связке, а повторное нажатие не плодит дубликаты.
 */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TShirtRepository tShirtRepository;
    @Mock
    private TShirtService tShirtService;

    private FavoriteService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteService(userRepository, tShirtRepository, tShirtService);
    }

    private static UserPrincipal shopper() {
        return new UserPrincipal(7L, "shopper@example.com", "hash", false);
    }

    private static User userWithFavorites(TShirt... favorites) {
        User user = new User();
        user.setId(7L);
        user.setEmail("shopper@example.com");
        user.setFavorites(new ArrayList<>(List.of(favorites)));
        return user;
    }

    private static TShirt shirt(long id, boolean disabled) {
        TShirt tShirt = new TShirt();
        tShirt.setId(id);
        tShirt.setName("Футболка " + id);
        tShirt.setDisabled(disabled);
        return tShirt;
    }

    @Test
    void listReturnsCardsOfAllFavoritedProducts() {
        User user = userWithFavorites(shirt(1L, false), shirt(2L, false));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(tShirtService.asDtoList(anyList())).thenReturn(List.of(
                new TShirtDto(1L, "Футболка 1", null, "M", 100.0, true, false, null, null, List.of()),
                new TShirtDto(2L, "Футболка 2", null, "L", 200.0, true, false, null, null, List.of())));

        List<TShirtDto> cards = service.list(shopper());

        assertThat(cards).hasSize(2);
    }

    @Test
    void favoriteIdsAreCheapForCatalog() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithFavorites(shirt(3L, false), shirt(4L, false))));

        assertThat(service.favoriteIds(shopper())).containsExactly(3L, 4L);
        verify(tShirtService, never()).asDtoList(anyList());
    }

    @Test
    void addLinksProductToCurrentUserFromToken() {
        User user = userWithFavorites();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(tShirtRepository.findById(5L)).thenReturn(Optional.of(shirt(5L, false)));

        service.add(shopper(), 5L);

        assertThat(user.getFavorites()).singleElement().satisfies(tShirt ->
                assertThat(tShirt.getId()).isEqualTo(5L));
        verify(userRepository).save(user);
    }

    @Test
    void addingSameProductTwiceDoesNotDuplicate() {
        User user = userWithFavorites(shirt(5L, false));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(tShirtRepository.findById(5L)).thenReturn(Optional.of(shirt(5L, false)));

        service.add(shopper(), 5L);

        assertThat(user.getFavorites()).hasSize(1);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void hiddenProductCannotBeAddedToFavorites() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithFavorites()));
        when(tShirtRepository.findById(9L)).thenReturn(Optional.of(shirt(9L, true)));

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.add(shopper(), 9L));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void removeUnlinksFavorite() {
        User user = userWithFavorites(shirt(5L, false), shirt(6L, false));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.remove(shopper(), 5L);

        assertThat(user.getFavorites()).extracting(TShirt::getId).containsExactly(6L);
        verify(userRepository).save(user);
    }

    @Test
    void removingProductThatWasNotFavoriteIsQuietNoop() {
        User user = userWithFavorites(shirt(5L, false));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.remove(shopper(), 404L);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.favoriteIds(shopper()));
    }
}
