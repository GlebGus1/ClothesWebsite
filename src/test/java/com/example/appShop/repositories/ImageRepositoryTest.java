package com.example.appShop.repositories;

import com.example.appShop.entities.Image;
import com.example.appShop.entities.TShirt;
import com.example.appShop.repositories.projection.ImageIdProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Тест репозитория на embedded-базе (H2 в режиме MySQL).
 *
 * Зачем он нужен, если юнит-тестов сервиса хватает: JPQL с проекцией компилирует не
 * javac, а Hibernate во время работы. Здесь проверяем, что запрос findImageIdsOfProducts
 * действительно парсится, отдаёт ТОЛЬКО id (а не longblob-байты картинок) и сохраняет
 * порядок «по id картинки» — он важен для листания фото в карточке каталога.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:images;MODE=MySQL;DB_CLOSE_DELAY=-1")
class ImageRepositoryTest {

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void returnsImageIdsOfRequestedProductsInIdOrder() {
        TShirt first = entityManager.persist(product("Первая"));
        TShirt second = entityManager.persist(product("Вторая"));
        Image firstOld = image(first, "a-old.png");
        Image firstNew = image(first, "a-new.png");
        Image secondImage = image(second, "b.png");
        entityManager.flush();

        List<ImageIdProjection> rows =
                imageRepository.findImageIdsOfProducts(List.of(first.getId(), second.getId()));

        assertThat(rows)
                .extracting(ImageIdProjection::getProductId, ImageIdProjection::getImageId)
                .containsExactly(
                        tuple(first.getId(), firstOld.getId()),
                        tuple(first.getId(), firstNew.getId()),
                        tuple(second.getId(), secondImage.getId()));
    }

    @Test
    void productsWithoutImagesAreAbsentInResult() {
        TShirt withImage = entityManager.persist(product("С картинкой"));
        TShirt withoutImage = entityManager.persist(product("Без картинки"));
        Image image = image(withImage, "only.png");
        entityManager.flush();

        List<ImageIdProjection> rows =
                imageRepository.findImageIdsOfProducts(List.of(withImage.getId(), withoutImage.getId()));

        // пустого «хвоста» из null-строк быть не должно: пустой список картинок сервис
        // трактует как «у товара нет фото»
        assertThat(rows).extracting(ImageIdProjection::getImageId).containsExactly(image.getId());
    }

    private Image image(TShirt tShirt, String fileName) {
        Image image = new Image();
        image.setName(fileName);
        image.setOriginalFileName(fileName);
        image.setContentType("image/png");
        image.setSize(4L);
        image.setBytes(new byte[]{1, 2, 3, 4});
        image.settShirt(tShirt);
        return entityManager.persist(image);
    }

    private TShirt product(String name) {
        TShirt tShirt = new TShirt();
        tShirt.setName(name);
        tShirt.setSize("M");
        tShirt.setPrice(100.0);
        tShirt.setAvailable(true);
        return tShirt;
    }
}
