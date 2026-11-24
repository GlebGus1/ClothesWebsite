import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {listFavorites} from '../api/favorites';
import {toApiError, type ApiRequestError} from '../api/http';
import {useFavorites} from '../context/FavoritesContext';
import {FavoritesNotice} from './FavoritesNotice';
import {ProductCard} from './ProductCard';
import {Spinner} from './Spinner';
import type {TShirtDto} from '../types/api';

/**
 * Вкладка «Избранное» в профиле.
 *
 * Список берём с сервера (GET /api/favorites возвращает карточки целиком), а
 * сердечки в каталоге закрашиваются по лёгкому запросу /api/favorites/ids — так
 * каталог не тянет лишние данные.
 *
 * Когда карточку убирают из избранного прямо здесь, FavoritesContext меняет ids —
 * и мы убираем её с экрана без повторного запроса (фильтр по ids).
 */
export function FavoritesPanel() {
  const {ids, loaded} = useFavorites();
  const [products, setProducts] = useState<TShirtDto[] | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);

  useEffect(() => {
    let alive = true;
    listFavorites()
      .then((result) => {
        if (alive) {
          setProducts(result);
        }
      })
      .catch((err) => {
        if (alive) {
          setError(toApiError(err));
        }
      });
    return () => {
      alive = false;
    };
  }, []);

  if (error) {
    return <p className='alert alert--error'>{error.message}</p>;
  }
  if (!products) {
    return <Spinner />;
  }

  // ids уже загрузились -> показываем только то, что осталось избранным
  const visible = loaded ? products.filter((product) => ids.includes(product.id)) : products;

  if (visible.length === 0) {
    return (
      <p className='empty'>
        В избранном пока пусто. Нажми на сердечко в каталоге — товар появится здесь.{' '}
        <Link to='/catalog'>выбрать футболки</Link>
      </p>
    );
  }

  return (
    <>
      <FavoritesNotice />
      <div className='grid'>
        {visible.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>
    </>
  );
}
