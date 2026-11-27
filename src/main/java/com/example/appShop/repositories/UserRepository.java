package com.example.appShop.repositories;

import com.example.appShop.entities.User;
import com.example.appShop.repositories.projection.FavoriteCountRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findUserByEmail(@Param("email") String email);

    /**
     * Проверка «не в чёрном списке» для запроса с готовым access-токеном:
     * один запрос по первичному ключу, без выборки строки целиком.
     */
    boolean existsByIdAndBannedTrue(long id);

    /**
     * Топ товаров по «сердечкам»: считаем строки связки user_favorites через JPQL
     * (join u.favorites f), а не ходим по пользователям в цикле.
     */
    @Query("select f.id as tshirtId, count(f) as favoritesCount from User u join u.favorites f "
            + "group by f.id order by count(f) desc, f.id asc")
    List<FavoriteCountRow> findFavoriteCounts(Pageable pageable);
}
