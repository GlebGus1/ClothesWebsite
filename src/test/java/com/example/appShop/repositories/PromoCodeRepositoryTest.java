package com.example.appShop.repositories;

import com.example.appShop.entities.PromoCode;
import org.junit.jupiter.api.Test;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тест репозитория промокодов на embedded-базе (H2 в режиме MySQL).
 *
 * Зачем он нужен, если юнит-тесты PromoCodeService есть:
 *  1) derived-запросы (findFirstByCodeIgnoreCase, findAllByOrderByIdDesc) проверяет не
 *     javac, а Hibernate во время работы — опечатку в имени метода он заметит только здесь;
 *  2) проверка уникальности в сервисе — это удобная подсказка пользователю, но последнее
 *     слово за БД: без уникального индекса два параллельных запроса создали бы дубликат;
 *  3) заодно Hibernate строит метамодель всех сущностей, то есть здесь видно, что таблицы
 *     promo_codes и пользовательского избранного (user_favorites) описаны корректно.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:promocodes;MODE=MySQL;DB_CLOSE_DELAY=-1")
class PromoCodeRepositoryTest {

    @Autowired
    private PromoCodeRepository promoCodeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsCodeRegardlessOfCase() {
        persist("SALE2024", 15, true, null);

        Optional<PromoCode> found = promoCodeRepository.findFirstByCodeIgnoreCase("sale2024");
        assertThat(found).isPresent();
        assertThat(found.get().getDiscountPercent()).isEqualTo(15);

        assertThat(promoCodeRepository.existsByCodeIgnoreCase("SaLe2024")).isTrue();
        assertThat(promoCodeRepository.existsByCodeIgnoreCase("NOPE")).isFalse();
    }

    @Test
    void duplicateCodeIsRejectedByDatabase() {
        persist("SALE2024", 15, true, null);

        PromoCode duplicate = new PromoCode();
        duplicate.setCode("SALE2024");  // тот же самый код
        duplicate.setDiscountPercent(20);
        duplicate.setActive(true);

        // В MySQL индекс unique сравнивает строки без учёта регистра (коллация *_ci), а в H2
        // сравнение чувствительно к регистру, поэтому дубль берём с тем же текстом. От
        // «SALE2024» vs «sale2024» защищает сервис: он проверяет existsByCodeIgnoreCase.
        //
        // INSERT происходит прямо внутри persist(): у PromoCode стратегия ключей
        // GenerationType.IDENTITY, поэтому Hibernate вынужден выполнить запрос сразу, чтобы
        // получить id. В приложении ошибку БД переводит в DataIntegrityViolationException
        // слой Spring Data, а здесь мы видим исходную Hibernate-ошибку — принимаем любой из
        // двух вариантов, чтобы тест не зависел от способа вызова.
        assertThatThrownBy(() -> entityManager.persist(duplicate))
                .isInstanceOfAny(ConstraintViolationException.class, DataIntegrityViolationException.class);
    }

    @Test
    void newestCodesComeFirstForAdminList() {
        persist("OLD", 5, true, null);
        persist("MIDDLE", 10, true, null);
        persist("NEW", 25, true, Instant.now());

        assertThat(promoCodeRepository.findAllByOrderByIdDesc())
                .extracting(PromoCode::getCode)
                .containsExactly("NEW", "MIDDLE", "OLD");
    }

    @Test
    void expiredAndSwitchedOffCodesAreStillReadableForAdmin() {
        // администратор видит и выключенные, и просроченные коды: фильтр «активен ли код»
        // живёт в PromoCodeService, а не в запросе
        persist("OFF", 10, false, null);
        persist("EXPIRED", 20, true, Instant.now().minusSeconds(60));

        assertThat(promoCodeRepository.findAllByOrderByIdDesc())
                .extracting(PromoCode::getCode)
                .containsExactly("EXPIRED", "OFF");
    }

    private PromoCode persist(String code, int discountPercent, boolean active, Instant expiresAt) {
        PromoCode promoCode = new PromoCode();
        promoCode.setCode(code);
        promoCode.setDiscountPercent(discountPercent);
        promoCode.setActive(active);
        promoCode.setExpiresAt(expiresAt);
        return entityManager.persist(promoCode);
    }
}
