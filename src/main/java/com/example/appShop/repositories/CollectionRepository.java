package com.example.appShop.repositories;

import com.example.appShop.entities.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Тематические коллекции (дропы).
 *
 * findByActiveTrueOrderByIdDesc — витрина (баннер и чипсы на каталоге): неактивные
 * коллекции покупателю не показываются вообще.
 */
@Repository
public interface CollectionRepository extends JpaRepository<Collection, Long> {

    List<Collection> findAllByOrderByActiveDescIdDesc();

    List<Collection> findByActiveTrueOrderByIdDesc();

    Optional<Collection> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /** Товаров в коллекции (для счётчика в админке). Считаем сразу во всех коллекциях одним запросом. */
    @Query("select t.collection.id as collectionId, count(t) as productsCount from com.example.appShop.entities.TShirt t "
            + "where t.collection is not null group by t.collection.id")
    List<CollectionProductsRow> countProducts();

    interface CollectionProductsRow {
        Long getCollectionId();

        Long getProductsCount();
    }

    /** id коллекций, которые сейчас использует хотя бы один товар — нужно при удалении коллекции. */
    @Query("select distinct t.collection.id from com.example.appShop.entities.TShirt t "
            + "where t.collection.id in :ids")
    List<Long> findUsedCollectionIds(@Param("ids") List<Long> ids);
}
