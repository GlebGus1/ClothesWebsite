import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {adminOrders, changeOrderStatus, orderStatuses} from '../../api/admin';
import {toApiError, type ApiRequestError} from '../../api/http';
import {Pagination} from '../../components/Pagination';
import {Spinner} from '../../components/Spinner';
import type {OrderDto, OrderStatus, PageResponse} from '../../types/api';
import {dateTime, money, statusLabel} from '../../utils/format';

const FALLBACK_STATUSES = ['PROCESSING', 'IN_TRANSIT', 'DELIVERED', 'CANCELED'];

/** Админ-список всех заказов + смена статуса (PATCH /api/admin/orders/{id}/status). */
export function AdminOrdersPage() {
  const [page, setPage] = useState<PageResponse<OrderDto> | null>(null);
  const [statuses, setStatuses] = useState<string[]>([]);
  const [pageNumber, setPageNumber] = useState(0);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    orderStatuses()
      .then(setStatuses)
      .catch(() => setStatuses(['PROCESSING', 'IN_TRANSIT', 'DELIVERED', 'CANCELED']));
  }, []);

  useEffect(() => {
    adminOrders(pageNumber, 20)
      .then(setPage)
      .catch((err) => setError(toApiError(err)));
  }, [pageNumber]);

  async function handleStatus(orderId: number, status: string) {
    setBusyId(orderId);
    try {
      const updated = await changeOrderStatus(orderId, status as OrderStatus);
      setPage((current) =>
        current ? {...current, content: current.content.map((item) => (item.id === orderId ? updated : item))} : current,
      );
      setError(null);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyId(null);
    }
  }

  if (error && !page) {
    return <p className='alert alert--error'>{error.message}</p>;
  }
  if (!page) {
    return <Spinner />;
  }

  return (
    <section className='page'>
      <h1 className='page__title'>Все заказы (админ)</h1>
      {error && <p className='alert alert--error'>{error.message}</p>}

      <div className='table-wrap'>
        <table className='table'>
          <thead>
            <tr>
              <th>№</th>
              <th>Покупатель</th>
              <th>Доставка</th>
              <th>Товары</th>
              <th>Сумма</th>
              <th>Создан</th>
              <th>Статус</th>
            </tr>
          </thead>
          <tbody>
            {page.content.map((order) => (
              <tr key={order.id}>
                <td>
                  <Link to={`/orders/${order.id}`}>#{order.id}</Link>
                </td>
                <td>
                  {order.customerEmail ?? '—'}
                  <div className='muted'>{order.nameOfUser}</div>
                </td>
                <td>
                  {order.postOffice}
                  <div className='muted'>индекс {order.postalCode} · {order.phoneNumber}</div>
                </td>
                <td>{order.items.map((item) => `${item.name} (${item.size ?? '—'})`).join(', ')}</td>
                <td>{money(order.items.reduce((sum, item) => sum + (item.price ?? 0), 0))}</td>
                <td>{dateTime(order.createdAt)}</td>
                <td>
                  <select
                    value={order.status}
                    disabled={busyId === order.id}
                    onChange={(event) => handleStatus(order.id, event.target.value)}
                    aria-label={`Статус заказа ${order.id}`}
                  >
                    {(statuses.length ? statuses : FALLBACK_STATUSES).map((status) => (
                      <option key={status} value={status}>
                        {statusLabel(status)}
                      </option>
                    ))}
                  </select>
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
