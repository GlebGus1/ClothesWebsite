package com.example.appShop.repositories;

import com.example.appShop.entities.TShirt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TShirtRepository extends JpaRepository<TShirt, Long> {
    //    List<TShirt> findAllByOrderBySize();
    List<TShirt> findAllByOrderByPriceDesc();

    List<TShirt> findByAvailableFalse();

    List<TShirt> findBySize(String size);

    List<TShirt> findAllByOrderByAvailable();
}
