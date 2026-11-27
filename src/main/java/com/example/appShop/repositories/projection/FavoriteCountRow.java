package com.example.appShop.repositories.projection;

/** Сколько раз товар добавили в избранное («сердечки»). */
public interface FavoriteCountRow {

    Long getTshirtId();

    Long getFavoritesCount();
}
