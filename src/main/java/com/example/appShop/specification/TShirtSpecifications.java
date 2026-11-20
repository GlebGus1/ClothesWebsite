package com.example.appShop.specification;

import com.example.appShop.entities.TShirt;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Динамические фильтры каталога.
 *
 * Спецификация = предикат, который Hibernate собирает в SQL. Можно комбинировать
 * («размер M И в наличии И не скрыт»), не плодя десятки методов в репозитории.
 * Именно это чинит старый баг, когда выбранная сортировка/фильтр хранились в полях контроллера.
 */
public final class TShirtSpecifications {

    private TShirtSpecifications() {
    }

    public static Specification<TShirt> byFilters(String size, Boolean available, boolean includeDisabled) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (size != null && !size.isBlank()) {
                predicates.add(cb.equal(root.get("size"), size));
            }
            if (available != null) {
                predicates.add(cb.equal(root.get("available"), available));
            }
            if (!includeDisabled) {
                predicates.add(cb.isFalse(root.get("isDisabled")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
