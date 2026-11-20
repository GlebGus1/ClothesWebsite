package com.example.appShop.repositories;

import com.example.appShop.entities.TShirt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TShirtRepository extends JpaRepository<TShirt, Long>, JpaSpecificationExecutor<TShirt> {

    /** Размеры для фильтра на фронтенде (чтобы не хардкодить весь диапазон размеров). */
    @Query("select distinct t.size from TShirt t where t.isDisabled = false and t.size is not null order by t.size")
    List<String> findDistinctSizes();
}
