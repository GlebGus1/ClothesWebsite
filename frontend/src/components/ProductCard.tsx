import {useState, type MouseEvent} from 'react';
import {Link} from 'react-router-dom';
import {imageUrl} from '../api/http';
import {FavoriteButton} from './FavoriteButton';
import {useCart} from '../context/CartContext';
import type {CSSProperties} from 'react';
import type {TShirtDto} from '../types/api';
import {money} from '../utils/format';

/**
 * Карточка товара в каталоге.
 *
 * Картинку берём по id: бэкенд отдаёт /api/images/{id} (эндпоинт публичный — можно
 * писать прямо в <img src>, без axios и токена). Вместе с обложкой бэкенд приносит
 * imageIds — id всех фото товара, причём главное фото идёт первым. Этого хватает,
 * чтобы листать галерею прямо в карточке, не дёргая API за карточкой целиком.
 *
 * Листание ПЛАВНОЕ: все фото стоят в один ряд (flex) внутри обрезанного окна
 * .card__viewport, а смена фото сдвигает ряд на .card__track на 100% ширины
 * через transition: transform 300ms ease-in-out. Никакой перерисовки и «прыжка».
 *
 * Стрелки и сердечко — ССЫЛКОЙ не являются: они лежат над ссылкой на товар и
 * перекрывают её сверху. preventDefault + stopPropagation гарантируют, что клик
 * по стрелке или сердцу не откроет страницу товара.
 *
 * Коллекция (дроп): если товар относится к дропу, карточка получает бейдж с её
 * названием и акцентную рамку цвета коллекции. Цвет приходит из БД и уже проверен
 * там регуляркой как HEX, поэтому его можно безопасно положить в CSS-переменную.
 */
export function ProductCard({product}: {product: TShirtDto}) {
  const {add, has} = useCart();
  const soldOut = !product.available;
  const [index, setIndex] = useState(0);

  const accent: CSSProperties | undefined = product.collectionBadgeColor
    ? ({'--accent': product.collectionBadgeColor} as CSSProperties)
    : undefined;
  const gallery = galleryIds(product);
  const position = gallery.length === 0 ? 0 : Math.min(index, gallery.length - 1);

  function shift(step: number) {
    setIndex((current) => (current + step + gallery.length) % gallery.length);
  }

  function onArrow(event: MouseEvent<HTMLButtonElement>, step: number) {
    event.preventDefault();
    event.stopPropagation();
    shift(step);
  }

  return (
    <article
      className={`card ${soldOut ? 'is-muted' : ''} ${product.collectionName ? 'card--collection' : ''}`}
      style={accent}
    >
      <div className='card__media'>
        <Link to={`/product/${product.id}`} className='card__link'>
          {gallery.length === 0 ? (
            <span className='card__placeholder'>нет фото</span>
          ) : (
            <div className='card__viewport'>
              {/* сдвиг всего ряда на N * 100% и есть анимация перелистывания */}
              <div className='card__track' style={{transform: `translateX(-${position * 100}%)`}}>
                {gallery.map((imageId, slide) => (
                  <div className='card__slide' key={imageId}>
                    <img
                      src={imageUrl(imageId)}
                      alt={`${product.name}, фото ${slide + 1} из ${gallery.length}`}
                      loading='lazy'
                    />
                  </div>
                ))}
              </div>
            </div>
          )}
        </Link>

        <FavoriteButton tshirtId={product.id} className='fav--overlay' />

        {product.collectionName && (
          <span className='card__drop' style={accent} title={`Коллекция ${product.collectionName}`}>
            {product.collectionName}
          </span>
        )}

        {gallery.length > 1 && (
          <>
            <button type='button' className='card__arrow card__arrow--prev' onClick={(event) => onArrow(event, -1)} aria-label='Предыдущее фото'>
              <span aria-hidden='true'>‹</span>
            </button>
            <button type='button' className='card__arrow card__arrow--next' onClick={(event) => onArrow(event, 1)} aria-label='Следующее фото'>
              <span aria-hidden='true'>›</span>
            </button>
            <span className='card__counter'>
              {position + 1}/{gallery.length}
            </span>
          </>
        )}

        {soldOut && <span className='card__flag'>продано</span>}
      </div>

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
          // товар куплен — кнопка остаётся на месте (сетка не «прыгает»), но неактивна
          <button type='button' className='btn btn--primary' disabled title='Эта вещь уже продана'>
            в корзину
          </button>
        ) : (
          <button type='button' className='btn btn--primary' onClick={() => add(product)} disabled={has(product.id)}>
            {has(product.id) ? 'в корзине ✓' : 'в корзину'}
          </button>
        )}
      </div>
    </article>
  );
}

/**
 * Id картинок для листания. Если бэкенд почему-то не принёс imageIds (старая
 * сборка, кэш), откатываемся на одно фото — обложку.
 */
function galleryIds(product: TShirtDto): number[] {
  if (product.imageIds?.length) {
    return product.imageIds;
  }
  return product.previewImageId ? [product.previewImageId] : [];
}
