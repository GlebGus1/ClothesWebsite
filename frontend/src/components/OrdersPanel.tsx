import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {myOrders} from '../api/orders';
import {toApiError, type ApiRequestError} from '../api/http';
import {Pagination} from './Pagination';
import {Spinner} from './Spinner';
import type {OrderDto, PageResponse} from '../types/api';
import {dateTime, money, statusLabel} from '../utils/format';

/**
 * Таблица «Мои заказы» — отдельный компонент, потому что список заказов нужен
 * и на странице /orders, и во вкладке профиля. Одна таблица = один код,
 * изменения (например, новые колонки) не надо повторять в двух местах.
 *
 * Сумма берём ИЗ ОТВЕТА СЕРВЕРА (order.total), а не суммируем цены сами:
 * OrderMapper уже вычитает скидку промокода, и у пользователя на экране ровно те
 * цифры, по которым создан заказ.
 */
export function OrdersPanel() {
  const [page, setPage] = useState<PageResponse<OrderDto> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [error, setError] = useState<ApiRequestError | null>(null);

  useEffect(() => {
    myOrders(pageNumber, 10)
      .then(setPage)
      .catch((err) => setError(toApiError(err)));
  }, [pageNumber]);

  if (error) {
    return <p className='alert alert--error'>{error.message}</p>;
  }
  if (!page) {
    return <Spinner />;
  }

  return (
    <>
      {page.content.length === 0 && (
        <p className='empty'>
          Заказов пока нет. <Link to='/catalog'>выбрать товары</Link>
        </p>
      )}

      <div className='table-wrap'>
        <table className='table'>
          <thead>
            <tr>
              <th>№</th>
              <th>Создан</th>
              <th>Товары</th>
              <th>Сумма</th>
              <th>Статус</th>
            </tr>
          </thead>
          <tbody>
            {page.content.map((order) => (
              <tr key={order.id}>
                <td>
                  <Link to={`/orders/${order.id}`}>#{order.id}</Link>
                </td>
                <td>{dateTime(order.createdAt)}</td>
                <td>{order.items.map((item) => item.name).join(', ')}</td>
                <td>
                  {money(order.total)}
                  {order.promoCode && (
                    <div className='form__hint'>
                      промокод {order.promoCode} −{order.discountPercent}% (скидка {money(order.discountAmount)})
                    </div>
                  )}
                </td>
                <td>
                  <span className={`badge badge--${order.status.toLowerCase()}`}>{statusLabel(order.status)}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination page={page} onChange={setPageNumber} />
    </>
  );
}
