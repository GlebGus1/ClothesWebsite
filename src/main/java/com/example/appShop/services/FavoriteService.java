package com.example.appShop.services;

import com.example.appShop.dto.TShirtDto;
import com.example.appShop.entities.TShirt;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.repositories.UserRepository;
import com.example.appShop.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Избранное («сердечко» в каталоге).
 *
 * Владельца берём ТОЛЬКО из токена (UserPrincipal), id пользователя в запросе не
 * передаётся — подставить чужое избранное невозможно.
 *
 * Как это лежит в БД: связь Many-to-Many объявлена на стороне User и живёт в
 * отдельной таблице-связке user_favorites (user_id, tshirt_id). Существующие таблицы
 * мы не трогаем: добавляется только новая таблица (spring.jpa.hibernate.ddl-auto=update).
 */
@Service
public class FavoriteService {

    private final UserRepository userRepository;
    private final TShirtRepository tShirtRepository;
    private final TShirtService tShirtService;

    public FavoriteService(UserRepository userRepository,
                           TShirtRepository tShirtRepository,
                           TShirtService tShirtService) {
        this.userRepository = userRepository;
        this.tShirtRepository = tShirtRepository;
        this.tShirtService = tShirtService;
    }

    /** Полный список избранного для вкладки «Избранное» в профиле. */
    @Transactional(readOnly = true)
    public List<TShirtDto> list(UserPrincipal principal) {
        return tShirtService.asDtoList(favoritesOf(principal));
    }

    /**
     * Только id избранных товаров: каталог получает их одним лёгким запросом,
     * чтобы закрасить сердечки на карточках, не гоняя JSON с названиями и картинками.
     */
    @Transactional(readOnly = true)
    public List<Long> favoriteIds(UserPrincipal principal) {
        return favoritesOf(principal).stream().map(TShirt::getId).toList();
    }

    /** Добавление идемпотентно: повторный нажатие не создаёт дубликат в связи. */
    @Transactional
    public void add(UserPrincipal principal, long tShirtId) {
        User user = getUser(principal);
        TShirt tShirt = getProduct(tShirtId);

        boolean already = user.getFavorites().stream().anyMatch(favorite -> favorite.getId().equals(tShirtId));
        if (!already) {
            user.getFavorites().add(tShirt);
            userRepository.save(user);
        }
    }

    @Transactional
    public void remove(UserPrincipal principal, long tShirtId) {
        User user = getUser(principal);
        // Удаляем по id, а не object.equals(): у TShirt нет своего equals/hashCode,
        // поэтому сравнение «по объекту» зависело бы от того, тот ли это экземпляр
        // в рамках текущей сессии Hibernate.
        boolean removed = user.getFavorites().removeIf(favorite -> favorite.getId().equals(tShirtId));
        if (removed) {
            userRepository.save(user);
        }
    }

    private List<TShirt> favoritesOf(UserPrincipal principal) {
        // Список возвращается внутри транзакции: favorites — LAZY-связь, и вне
        // сессии Hibernate бросил бы LazyInitializationException (open-in-view=false).
        return List.copyOf(getUser(principal).getFavorites());
    }

    private User getUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("Пользователь", principal.getId()));
    }

    /** Скрытый из каталога товар в избранное не добавляем (его всё равно никто не купит). */
    private TShirt getProduct(long tShirtId) {
        return tShirtRepository.findById(tShirtId)
                .filter(tShirt -> !tShirt.isDisabled())
                .orElseThrow(() -> new NotFoundException("Футболка", tShirtId));
    }
}
