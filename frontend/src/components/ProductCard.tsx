import {Link} from 'react-router-dom';
import {imageUrl} from '../api/http';
import {useCart} from '../context/CartContext';
import type {TShirtDto} from '../types/api';
import {money} from '../utils/format';

/**
 * Карточка товара в каталоге. Картинку берём по previewImageId: бэкенд отдаёт id,
 * а браузер сам грузит /api/images/{id} (эндпоинт публичный — можно использовать
 * напрямую в <img src>, без axios и без токена).
 */
export function ProductCard({product}: {product: TShirtDto}) {
  const {add, has} = useCart();
  const soldOut = !product.available;

  return (
    <article className={`card ${soldOut ? 'is-muted' : ''}`}>
      <Link to={`/product/${product.id}`} className='card__media'>
        {product.previewImageId ? (
          <img src={imageUrl(product.previewImageId)} alt={product.name} loading='lazy' />
        ) : (
          <span className='card__placeholder'>нет фото</span>
        )}
      </Link>

      <div className='card__body'>
        <h3 className='card__title'>
          <Link to={`/product/${product.id}`}>{product.name}</Link>
        </h3>
        <p className='card__meta'>
          размер: {product.size ?? '—'} · {money(product.price)}
        </p>
        {product.disabled && <p className='badge badge--muted'>скрыт из каталога</p>}
      </div>

      <div className='card__actions'>
        {soldOut ? (
          <span className='badge badge--muted'>нет в наличии</span>
        ) : (
          <button type='button' className='btn btn--primary' onClick={() => add(product)} disabled={has(product.id)}>
            {has(product.id) ? 'в корзине ✓' : 'в корзину'}
          </button>
        )}
      </div>
    </article>
  );
}
