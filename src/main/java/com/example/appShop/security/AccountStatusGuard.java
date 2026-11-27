package com.example.appShop.security;

import com.example.appShop.entities.User;
import com.example.appShop.exceptions.AccountBannedException;
import com.example.appShop.repositories.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Единая точка проверки «аккаунт заблокирован (чёрный список)».
 *
 * Зачем нужен отдельный компонент:
 *  1) запрет должен проверяться в нескольких местах — при входе (AuthService.login),
 *     при обновлении пары токенов (refresh) и при ЛЮБОМ запросе с ещё живым
 *     access-токеном (их срок — 15 минут, и за это время админ мог успеть забанить);
 *  2) чтобы нигде не разошёлся текст ошибки и код ответа (всегда 403 «Аккаунт заблокирован»).
 *
 * Access-токен — самодостаточная бумажка: в нём нет пометки «забанен», поэтому
 * единственный честный источник правды — строка users.banned в базе. Проверка стоит
 * один дешёвый запрос по первичному ключу (existsByIdAndBannedTrue).
 */
@Component
public class AccountStatusGuard {

    private final UserRepository userRepository;

    public AccountStatusGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Вариант «у нас уже есть сущность пользователя» — в базу не идём. */
    public void assertNotBanned(User user) {
        if (user != null && user.isBanned()) {
            throw new AccountBannedException();
        }
    }

    /** Вариант «у нас есть только id из токена» — проверяем одним запросом. */
    public void assertNotBanned(Long userId) {
        if (isBanned(userId)) {
            throw new AccountBannedException();
        }
    }

    /**
     * Запрос по первичному ключу, без выборки строки целиком.
     * Нужен фильтру JWT: там нельзя бросать исключение (оно минует
     * ExceptionTranslationFilter и превратится в 500), там ставят атрибут запроса
     * и дают ответить RestAuthenticationEntryPoint.
     */
    public boolean isBanned(Long userId) {
        return userId != null && userRepository.existsByIdAndBannedTrue(userId);
    }
}
