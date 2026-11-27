package com.example.appShop.specification;

import com.example.appShop.entities.TShirt;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Динамические фильтры каталога.
 *
 * Спецификация = предикат, который Hibernate собирает в SQL. Можно комбинировать
 * («размер M И в наличии И не скрыт И из коллекции 3»), не плодя десятки методов
 * в репозитории. Именно это чинит старый баг, когда выбранная сортировка/фильтр
 * хранились в полях контроллера.
 *
 * ВАЖНО: поле TShirt.collection объявлено как EAGER ManyToOne — Hibernate сам
 * доклеивает LEFT OUTER JOIN с таблицей collections в каждый запрос, поэтому
 * карточке не нужны отдельные походы за названием коллекции (нет N+1).
 * Здесь мы добавляем JOIN только когда коллекция участвует в ФИЛЬТРЕ.
 */
public final class TShirtSpecifications {

    private TShirtSpecifications() {
    }

    public static Specification<TShirt> byFilters(String size, Boolean available, Long collectionId,
                                                  boolean includeDisabled) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (size != null && !size.isBlank()) {
                predicates.add(cb.equal(root.get("size"), size));
            }
            if (available != null) {
                predicates.add(cb.equal(root.get("available"), available));
            }
            if (collectionId != null) {
                // LEFT, а не INNER: параметр мог прийти по «старой» ссылке из закладки
                // (коллекцию удалили) — тогда фильтр просто не найдёт товаров,
                // а запрос не упадёт на внутреннем соединении.
                predicates.add(cb.equal(root.join("collection", JoinType.LEFT).get("id"), collectionId));
            }
            if (!includeDisabled) {
                predicates.add(cb.isFalse(root.get("isDisabled")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
