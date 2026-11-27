import {useEffect, useState} from 'react';
import {Link, useLocation, useParams} from 'react-router-dom';
import {getOrder} from '../api/orders';
import {toApiError, type ApiRequestError} from '../api/http';
import {ApiErrorBox} from '../components/ApiErrorBox';
import {OrderItemsList} from '../components/OrderItemsList';
import {OrderTotals} from '../components/OrderTotals';
import {Spinner} from '../components/Spinner';
import type {OrderDto} from '../types/api';
import {dateTime, statusLabel} from '../utils/format';

/**
 * Карточка заказа. Право доступа проверяет бэкенд: пользователь видит только свои
 * заказы (403 на чужой), админ — любой.
 */
export function OrderDetailsPage() {
  const {id} = useParams<{id: string}>();
  const location = useLocation();
  const created = Boolean((location.state as {created?: boolean} | null)?.created);

  const [order, setOrder] = useState<OrderDto | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);

  useEffect(() => {
    getOrder(Number(id))
      .then(setOrder)
      .catch((err) => setError(toApiError(err)));
  }, [id]);

  if (error) {
    return (
      <section className='page'>
        <ApiErrorBox error={error} />
        <Link className='btn btn--ghost' to='/orders'>
          ← к списку заказов
        </Link>
      </section>
    );
  }

  if (!order) {
    return <Spinner />;
  }

  return (
    <section className='page'>
      {created && <p className='alert alert--success'>Заказ оформлен! Менеджер свяжется с вами.</p>}

      <h1 className='page__title'>
        Заказ #{order.id}{' '}
        <span className={`badge badge--${order.status.toLowerCase()}`}>{statusLabel(order.status)}</span>
      </h1>

      <div className='two-columns'>
        <div className='panel'>
          <h2>Доставка</h2>
          <ul className='facts'>
            <li>
              <span>Получатель</span>
              <b>{order.nameOfUser}</b>
            </li>
            <li>
              <span>Отделение</span>
              <b>{order.postOffice}</b>
            </li>
            <li>
              <span>Индекс</span>
              <b>{order.postalCode}</b>
            </li>
            <li>
              <span>Телефон</span>
              <b>{order.phoneNumber}</b>
            </li>
            <li>
              <span>Оформлен</span>
              <b>{dateTime(order.createdAt)}</b>
            </li>
            {order.customerEmail && (
              <li>
                <span>Покупатель</span>
                <b>{order.customerEmail}</b>
              </li>
            )}
          </ul>
        </div>

        <div className='panel'>
          <h2>Состав</h2>
          {/* тот же список позиции-карточкой, что и в админских заказах:
              фото, название, размер, цена; ссылкой на редактирование товара — только для админа */}
          <OrderItemsList items={order.items} />
          <OrderTotals order={order} />
        </div>
      </div>

      <Link className='btn btn--ghost' to='/orders'>
        ← к списку заказов
      </Link>
    </section>
  );
}
