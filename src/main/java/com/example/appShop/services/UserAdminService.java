package com.example.appShop.services;

import com.example.appShop.dto.UserDto;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.mapper.UserMapper;
import com.example.appShop.security.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Админские операции над пользователями: список, блокировка («чёрный список»), разблокировка.
 *
 * Что делает блокировка и почему именно так:
 *  1) users.banned = true — дальше вход (login), обновление пары токенов (refresh) и любой
 *     запрос с живым access-токеном получают 403 «Аккаунт заблокирован»;
 *  2) ОДНОВРЕМЕННО отзываем ВСЕ refresh-токены пользователя. Это принципиально:
 *     access-токен «бумажка» на 15 минут, мы его не можем отменить, но если отозван
 *     refresh, клиент не сможет получить новый access — и максимум через 15 минут
 *     заблокированный аккаунт гарантированно «отваливается» сам.
 *  3) Сам себя заблокировать нельзя: админ с единственной учёткой, нажавший «Заблокировать»
 *     напротив своей строки, отрезал бы себе доступ к админке (и разбанить себя
 *     через интерфейс уже не смог бы).
 *
 * Отдельный сервис (а не методы в UserService) держит UserService «про аккаунты»,
 * а этот — про админские решения, которые задевают ещё и подсистему токенов.
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    public UserAdminService(UserService userService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        return userService.findAll().stream().map(UserMapper::toDto).toList();
    }

    /**
     * @param actingAdminId id администратора, который нажал кнопку (из его access-токена)
     */
    @Transactional
    public UserDto setBanned(long userId, boolean banned, long actingAdminId) {
        if (userId == actingAdminId) {
            throw new InvalidRequestException(
                    "Нельзя заблокировать собственный аккаунт: вы потеряете доступ к админке");
        }

        User user = userService.getByIdOrThrow(userId);
        if (user.isBanned() == banned) {
            // Повторный клик («забанить» уже забаненного) — не ошибка, просто ничего не меняем
            return UserMapper.toDto(user);
        }

        user.setBanned(banned);
        User saved = userService.save(user);

        int revokedTokens = 0;
        if (banned) {
            revokedTokens = refreshTokenService.revokeAllForUser(saved.getEmail());
        }

        log.info("Админ id={} {} пользователя id={} ({}); отозвано refresh-токенов: {}",
                actingAdminId, banned ? "заблокировал" : "разблокировал", saved.getId(),
                saved.getEmail(), revokedTokens);

        return UserMapper.toDto(saved);
    }
}
