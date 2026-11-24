import {useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {getTShirt} from '../api/catalog';
import {ApiRequestError, imageUrl, toApiError, type ApiRequestError as ApiError} from '../api/http';
import {ApiErrorBox} from '../components/ApiErrorBox';
import {FavoriteButton} from '../components/FavoriteButton';
import {FavoritesNotice} from '../components/FavoritesNotice';
import {Spinner} from '../components/Spinner';
import {useCart} from '../context/CartContext';
import type {ImageDto, TShirtDetailsDto} from '../types/api';
import {dateTime, money} from '../utils/format';

/**
 * Страница товара: галерея (стрелки + миниатюры + стрелки клавиатуры),
 * характеристики и кнопка «в корзину».
 *
 * Порядок картинок один и тот же везде: главное фото (previewImageId) первым,
 * остальные — по id. Совпадает с каталожной карточкой, поэтому «листать фото»
 * можно и из списка, и здесь.
 */
export function ProductPage() {
  const {id} = useParams<{id: string}>();
  const productId = Number(id);
  const navigate = useNavigate();
  const {add, has} = useCart();

  const [product, setProduct] = useState<TShirtDetailsDto | null>(null);
  const [activeImage, setActiveImage] = useState<number | null>(null);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    if (!Number.isFinite(productId) || productId <= 0) {
      setError(new ApiRequestError(undefined, 0, 'Некорректный id товара'));
      return;
    }
    getTShirt(productId)
      .then((loaded) => {
        setProduct(loaded);
        setActiveImage(loaded.previewImageId ?? loaded.images[0]?.id ?? null);
      })
      .catch((err) => setError(toApiError(err)));
  }, [productId]);

  // листание с клавиатуры: ← / → (активно, только когда фото действительно больше одного)
  useEffect(() => {
    const loaded = product;
    if (!loaded || loaded.images.length < 2) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
        return;
      }
      const step = event.key === 'ArrowLeft' ? -1 : 1;
      setActiveImage((current) => nextImageId(loaded, current, step));
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [product]);

  if (error && !product) {
    return (
      <section className='page'>
        <ApiErrorBox error={error} />
        <button type='button' className='btn btn--ghost' onClick={() => navigate('/catalog')}>
          ← в каталог
        </button>
      </section>
    );
  }

  if (!product) {
    return <Spinner />;
  }

  const gallery = previewFirst(product.images, product.previewImageId);
  const position = Math.max(
    0,
    gallery.findIndex((image) => image.id === (activeImage ?? product.previewImageId)),
  );

  const shift = (step: number) => {
    setActiveImage((current) => nextImageId(product, current, step));
  };

  return (
    <section className='page product'>
      <button type='button' className='btn btn--ghost back' onClick={() => navigate('/catalog')}>
        ← в каталог
      </button>

      <FavoritesNotice />

      <div className='product__layout'>
        <div className='product__gallery'>
          <div className='product__main-image'>
            {gallery.length > 0 ? (
              // Листание ПЛАВНОЕ (как в карточке каталога): все фото стоят в один ряд
              // внутри .product__viewport, а смена активного фото сдвигает ряд на
              // translateX(-N*100%); transition 300ms ease-in-out дорисовывает переход.
              <div className='product__viewport'>
                <div className='product__track' style={{transform: `translateX(-${position * 100}%)`}}>
                  {gallery.map((image, slide) => (
                    <div className='product__slide' key={image.id}>
                      <img
                        src={imageUrl(image.id)}
                        alt={`${product.name}, фото ${slide + 1} из ${gallery.length}`}
                        loading='lazy'
                      />
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <span className='card__placeholder'>нет фото</span>
            )}

            {/* сердечко: состояние берётся из FavoritesContext — одно и то же избранное
                в каталоге, здесь и во вкладке «Избранное» в профиле */}
            <FavoriteButton tshirtId={product.id} className='fav--overlay' />

            {gallery.length > 1 && (
              <>
                <button type='button' className='card__arrow card__arrow--prev' onClick={() => shift(-1)} aria-label='Предыдущее фото'>
                  <span aria-hidden='true'>‹</span>
                </button>
                <button type='button' className='card__arrow card__arrow--next' onClick={() => shift(1)} aria-label='Следующее фото'>
                  <span aria-hidden='true'>›</span>
                </button>
                <span className='card__counter'>
                  {position + 1}/{gallery.length}
                </span>
              </>
            )}
          </div>

          {gallery.length > 1 && (
            <div className='product__thumbs'>
              {gallery.map((image, index) => (
                <button
                  key={image.id}
                  type='button'
                  className={`thumb ${index === position ? 'is-active' : ''}`}
                  onClick={() => setActiveImage(image.id)}
                  title={image.originalFileName}
                >
                  <img src={imageUrl(image.id)} alt={image.originalFileName} loading='lazy' />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className='product__info'>
          <h1 className='page__title'>
            {product.name}
            {!product.available && <span className='badge badge--sold'>продано</span>}
          </h1>
          <p className='product__price'>{money(product.price)}</p>
          <ul className='product__facts'>
            <li>
              <span>Размер</span>
              <b>{product.size ?? '—'}</b>
            </li>
            <li>
              <span>Наличие</span>
              <b>{product.available ? 'в наличии' : 'продано'}</b>
            </li>
            <li>
              <span>Добавлен</span>
              <b>{dateTime(product.dateOfCreated)}</b>
            </li>
          </ul>
          {product.description && <p className='product__description'>{product.description}</p>}

          {product.available ? (
            <button type='button' className='btn btn--primary btn--large' onClick={() => add(product)} disabled={has(product.id)}>
              {has(product.id) ? 'уже в корзине ✓' : 'добавить в корзину'}
            </button>
          ) : (
            <p className='badge badge--sold'>эта вещь уже продана — у нас каждый экземпляр поштучно</p>
          )}
        </div>
      </div>
    </section>
  );
}

/** Главное фото — первым; дальше по id (как их отдаёт бэкенд). */
function previewFirst(images: ImageDto[], previewImageId: number | null): ImageDto[] {
  const preview = images.find((image) => image.id === previewImageId);
  if (!preview) {
    return images;
  }
  return [preview, ...images.filter((image) => image.id !== preview.id)];
}

/** Следующее фото по кругу (листание «зациклено», как в витринах маркетплейсов). */
function nextImageId(product: TShirtDetailsDto, currentId: number | null, step: number): number | null {
  const gallery = previewFirst(product.images, product.previewImageId);
  if (gallery.length < 2) {
    return currentId;
  }
  const index = Math.max(
    0,
    gallery.findIndex((image) => image.id === (currentId ?? product.previewImageId)),
  );
  return gallery[(index + step + gallery.length) % gallery.length].id;
}
