package com.example.appShop.repositories;

import com.example.appShop.entities.Image;
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
}
