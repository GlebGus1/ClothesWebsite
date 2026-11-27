package com.example.appShop.repositories;

import com.example.appShop.entities.TShirt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TShirtRepository extends JpaRepository<TShirt, Long>, JpaSpecificationExecutor<TShirt> {

    /** Размеры для фильтра на фронтенде (чтобы не хардкодить весь диапазон размеров). */
    @Query("select distinct t.size from TShirt t where t.isDisabled = false and t.size is not null order by t.size")
    List<String> findDistinctSizes();

    /**
     * Топ самых просматриваемых товаров для дашборда. Скрытые (is_disabled=true)
     * не показываем: «мёртвый» товар в статистике только портит картину.
     */
    @Query("select t from TShirt t where t.isDisabled = false "
            + "order by coalesce(t.viewsCount, 0) desc, t.id desc")
    List<TShirt> findMostViewed(Pageable pageable);

    /**
     * Счётчик просмотров правим атомарным UPDATE, а не «прочитали сущность → +1 → save»:
     * два параллельных открытия карточки не потеряют второй просмотр, и нам не нужно
     * держать сущность в сессии ради одной цифры.
     */
    @Modifying
    @Query("update TShirt t set t.viewsCount = coalesce(t.viewsCount, 0) + 1 where t.id = :id")
    int increaseViewsCount(@Param("id") long id);

    /**
     * При удалении коллекции отвязываем товары (set null), а не удаляем их:
     * коллекция — маркетинговая полка, товар жить без полки обязан.
     */
    @Modifying
    @Query("update TShirt t set t.collection = null where t.collection.id = :collectionId")
    int clearCollection(@Param("collectionId") long collectionId);

    @Query("select count(t) from TShirt t where t.collection.id = :collectionId")
    long countOfCollection(@Param("collectionId") long collectionId);

    long countByIsDisabledFalse();
}
