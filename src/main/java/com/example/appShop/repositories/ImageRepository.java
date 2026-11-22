package com.example.appShop.repositories;

import com.example.appShop.entities.Image;
import com.example.appShop.repositories.projection.ImageIdProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {

    // JPQL вместо derived-запроса: имя поля «tShirt» (с большой буквы T внутри)
    // Spring Data разбирает некорректно, поэтому запрос задаём явно.
    @Query("select i from Image i where i.tShirt.id = :tShirtId order by i.id asc")
    List<Image> findAllByTShirt(@Param("tShirtId") Long tShirtId);

    @Query("select count(i) from Image i where i.tShirt.id = :tShirtId")
    long countByTShirt(@Param("tShirtId") Long tShirtId);

    /**
     * Id всех картинок сразу для нескольких товаров (одна страница каталога).
     * Выбираем только id, а не сущность Image: иначе из базы уедут longblob-байты.
     * Пустой список параметров не передаём — «in ()» база не понимает (см. TShirtService).
     */
    @Query("select i.tShirt.id as productId, i.id as imageId from Image i "
            + "where i.tShirt.id in :productIds order by i.id asc")
    List<ImageIdProjection> findImageIdsOfProducts(@Param("productIds") List<Long> productIds);
}
