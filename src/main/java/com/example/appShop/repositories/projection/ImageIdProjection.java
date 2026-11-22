package com.example.appShop.repositories.projection;

/**
 * Read-only проекция «id картинки + id товара».
 *
 * Нужна, чтобы собрать галерею всей страницы каталога ОДНИМ запросом. Если бы
 * ImageRepository вернул список сущностей Image, Hibernate вытащил бы из MySQL
 * колонку longblob — сами байты картинок; для списка это дорого и бесполезно.
 * В проекции есть только алиасы JPQL-запроса (productId/imageId), и Spring Data
 * сопоставляет колонки результата по ИМЕНИ свойства: геттер вида getTShirtId()
 * описывает по правилам JavaBeans свойство «TShirtId» с большой буквы, алиас с ним
 * не совпадает, и вместо числа приходит null. Отсюда короткие имена методов.
 */
public interface ImageIdProjection {

    long getProductId();

    long getImageId();
}
