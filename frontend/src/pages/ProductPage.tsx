import {useEffect, useState} from 'react';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {getTShirt} from '../api/catalog';
import {ApiRequestError, imageUrl, toApiError, type ApiRequestError as ApiError} from '../api/http';
import {ApiErrorBox} from '../components/ApiErrorBox';
import {Spinner} from '../components/Spinner';
import {useCart} from '../context/CartContext';
import type {TShirtDetailsDto} from '../types/api';
import {dateTime, money} from '../utils/format';

/** Страница товара: галерея + характеристики + кнопка «в корзину». */
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

  if (error && !product) {
    return (
      <section className='page'>
        <ApiErrorBox error={error} />
        <Link className='btn btn--ghost' to='/catalog'>
          ← в каталог
        </Link>
      </section>
    );
  }

  if (!product) {
    return <Spinner />;
  }

  const shown = activeImage ?? product.previewImageId;

  return (
    <section className='page product'>
      <button type='button' className='btn btn--ghost back' onClick={() => navigate('/catalog')}>
        ← в каталог
      </button>

      <div className='product__layout'>
        <div className='product__gallery'>
          <div className='product__main-image'>
            {shown ? <img src={imageUrl(shown)} alt={product.name} /> : <span className='card__placeholder'>нет фото</span>}
          </div>
          {product.images.length > 1 && (
            <div className='product__thumbs'>
              {product.images.map((image) => (
                <button
                  key={image.id}
                  type='button'
                  className={`thumb ${shown === image.id ? 'is-active' : ''}`}
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
          <h1 className='page__title'>{product.name}</h1>
          <p className='product__price'>{money(product.price)}</p>
          <ul className='product__facts'>
            <li>
              <span>Размер</span>
              <b>{product.size ?? '—'}</b>
            </li>
            <li>
              <span>Наличие</span>
              <b>{product.available ? 'в наличии' : 'нет в наличии'}</b>
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
            <p className='badge badge--muted'>товара нет в наличии</p>
          )}
        </div>
      </div>
    </section>
  );
}
