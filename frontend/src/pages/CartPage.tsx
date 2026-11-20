import {Link} from 'react-router-dom';
import {imageUrl} from '../api/http';
import {useCart} from '../context/CartContext';
import {money} from '../utils/format';

/**
 * Корзина живёт в localStorage и отправляется на сервер списком id (tshirtIds).
 * Цены и названия здесь «для красоты»: итог к заказу сервер считает сам по ценам
 * из БД (OrderService), поэтому обмануть фронтенд изменённой ценой нельзя.
 */
export function CartPage() {
  const {items, remove, total, count} = useCart();

  if (count === 0) {
    return (
      <section className='page'>
        <h1 className='page__title'>Корзина</h1>
        <p className='empty'>
          Корзина пуста. <Link to='/catalog'>выбрать футболки</Link>
        </p>
      </section>
    );
  }

  return (
    <section className='page'>
      <h1 className='page__title'>Корзина ({count})</h1>

      <ul className='cart'>
        {items.map((item) => (
          <li key={item.id} className='cart__item'>
            <Link to={`/product/${item.id}`} className='cart__media'>
              {item.previewImageId ? (
                <img src={imageUrl(item.previewImageId)} alt={item.name} loading='lazy' />
              ) : (
                <span className='card__placeholder'>нет фото</span>
              )}
            </Link>
            <div className='cart__info'>
              <Link to={`/product/${item.id}`} className='cart__name'>
                {item.name}
              </Link>
              <span className='cart__meta'>размер: {item.size ?? '—'}</span>
            </div>
            <span className='cart__price'>{money(item.price)}</span>
            <button type='button' className='btn btn--ghost btn--small' onClick={() => remove(item.id)}>
              удалить
            </button>
          </li>
        ))}
      </ul>

      <div className='cart__total'>
        <span>Итого</span>
        <b>{money(total)}</b>
      </div>

      <div className='page__actions'>
        <Link to='/checkout' className='btn btn--primary btn--large'>
          оформить заказ
        </Link>
        <Link to='/catalog' className='btn btn--ghost'>
          продолжить покупки
        </Link>
      </div>
    </section>
  );
}
