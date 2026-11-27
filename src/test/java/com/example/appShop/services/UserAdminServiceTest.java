package com.example.appShop.services;

import com.example.appShop.dto.UserDto;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.security.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Блокировка аккаунтов («чёрный список») администратором.
 *
 * Здесь важны два обещания интерфейса:
 *  - админ не может выстрелить себе в ногу (забанить единственный доступ к админке);
 *  - бан немедленно отзывает refresh-токены, иначе «заблокированный» пользователь
 *    оставался бы в системе до перевыпуска токена.
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserAdminService userAdminService;

    private static User user(long id, String email, boolean banned) {
        User user = new User();
        user.setId(id);
        user.setFirstName("Мария");
        user.setLastName("Иванова");
        user.setEmail(email);
        user.setEnabled(true);
        user.setBanned(banned);
        return user;
    }

    @Test
    void adminCannotBanHimself() {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> userAdminService.setBanned(7L, true, 7L))
                .withMessageContaining("собственный аккаунт");

        // и тем более ничего не записывается: админ с единственной учёткой остался бы снаружи
        verify(userService, never()).getByIdOrThrow(anyLong());
        verify(userService, never()).save(any());
    }

    @Test
    void banFlipsFlagAndRevokesEveryRefreshToken() {
        User target = user(9L, "spam@example.com", false);
        when(userService.getByIdOrThrow(9L)).thenReturn(target);
        when(userService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto dto = userAdminService.setBanned(9L, true, 1L);

        assertThat(dto.banned()).isTrue();
        // отзыв refresh обязателен: access-токен живёт 15 минут и отменить его нельзя,
        // но без refresh новый access выдать уже нельзя
        verify(refreshTokenService).revokeAllForUser("spam@example.com");
    }

    @Test
    void unbanDoesNotReviveOldTokens() {
        User target = user(9L, "spam@example.com", true);
        when(userService.getByIdOrThrow(9L)).thenReturn(target);
        when(userService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto dto = userAdminService.setBanned(9L, false, 1L);

        assertThat(dto.banned()).isFalse();
        // разбанка не должна «возвращать» отозванные сессии: пользователь зайдёт заново
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void repeatedBanIsIdempotentAndDoesNotTouchTokensAgain() {
        when(userService.getByIdOrThrow(9L)).thenReturn(user(9L, "spam@example.com", true));

        UserDto dto = userAdminService.setBanned(9L, true, 1L);

        assertThat(dto.banned()).isTrue();
        verify(userService, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void listShowsBannedAndEnabledFlags() {
        when(userService.findAll()).thenReturn(List.of(user(1L, "a@example.com", false), user(2L, "b@example.com", true)));

        List<UserDto> users = userAdminService.listUsers();

        assertThat(users).hasSize(2);
        assertThat(users.get(0).banned()).isFalse();
        assertThat(users.get(0).enabled()).isTrue();
        assertThat(users.get(1).banned()).isTrue();
        assertThat(users.get(1).email()).isEqualTo("b@example.com");
    }
}
