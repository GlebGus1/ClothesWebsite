import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {myOrders} from '../api/orders';
import {toApiError, type ApiRequestError} from '../api/http';
import {Pagination} from '../components/Pagination';
import {Spinner} from '../components/Spinner';
import type {OrderDto, PageResponse} from '../types/api';
import {dateTime, money, statusLabel} from '../utils/format';

/** Список заказов текущего пользователя (/api/orders/me). */
export function OrdersPage() {
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
    <section className='page'>
      <h1 className='page__title'>Мои заказы</h1>

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
                <td>{money(order.items.reduce((sum, item) => sum + (item.price ?? 0), 0))}</td>
                <td>
                  <span className={`badge badge--${order.status.toLowerCase()}`}>{statusLabel(order.status)}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination page={page} onChange={setPageNumber} />
    </section>
  );
}
