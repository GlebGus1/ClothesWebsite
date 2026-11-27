import {useCallback, useEffect, useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {adminOrders, changeOrderStatus, orderStatuses, restoreOrderItems} from '../../api/admin';
import {toApiError, type ApiRequestError} from '../../api/http';
import {OrderItemsList} from '../../components/OrderItemsList';
import {Pagination} from '../../components/Pagination';
import {Spinner} from '../../components/Spinner';
import type {OrderDto, OrderStatus, PageResponse} from '../../types/api';
import {dateTime, money, statusLabel} from '../../utils/format';

const FALLBACK_STATUSES = ['PROCESSING', 'IN_TRANSIT', 'DELIVERED', 'CANCELED'];

/**
 * Все заказы + смена статуса + возврат позиций отменённого заказа в продажу.
 *
 * Фильтр по статусу живёт в URL (?status=CANCELED) и выполняется базой: страница
 * всегда содержит ровно pageSize подходящих заказов. Если бы фильтр делался «в Java»
 * после выдачи страницы, админ видел бы то 3 заказа на странице, то 15 — и пагинация
 * стала бы неправдой.
 *
 * Возврат в продаж — отдельная кнопка, а НЕ автоматический эффект смены статуса:
 * отмена заказа не доказывает, что вещь физически вернулась на склад.
 */
export function AdminOrdersPage() {
  const [page, setPage] = useState<PageResponse<OrderDto> | null>(null);
  const [statuses, setStatuses] = useState<string[]>([]);
  const [pageNumber, setPageNumber] = useState(0);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const [searchParams, setSearchParams] = useSearchParams();
  const status = searchParams.get('status') ?? '';

  const load = useCallback(() => {
    setError(null);
    return adminOrders(pageNumber, 20, status || undefined)
      .then(setPage)
      .catch((err) => setError(toApiError(err)));
  }, [pageNumber, status]);

  useEffect(() => {
    orderStatuses()
      .then(setStatuses)
      .catch(() => setStatuses(FALLBACK_STATUSES));
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function filterBy(next: string) {
    setPageNumber(0);
    setFeedback(null);
    const params = new URLSearchParams(searchParams);
    if (next === '') {
      params.delete('status');
    } else {
      params.set('status', next);
    }
    setSearchParams(params);
  }

  async function handleStatus(orderId: number, next: string) {
    setBusyId(orderId);
    setError(null);
    try {
      const updated = await changeOrderStatus(orderId, next as OrderStatus);
      setPage((current) =>
        current
          ? {...current, content: current.content.map((item) => (item.id === orderId ? updated : item))}
          : current,
      );
      setFeedback(
        next === 'CANCELED'
          ? `Заказ #${orderId} отменён. Товары сами в продажу НЕ вернулись — для этого есть кнопка «вернуть товары в продажу».`
          : `Заказ #${orderId}: статус «${statusLabel(next)}»`,
      );
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyId(null);
    }
  }

  async function handleRestoreItems(orderId: number) {
    if (
      !window.confirm(
        `Вернуть позиции заказа #${orderId} в продажу? Товары снова появятся в каталоге, ` +
          'но те, что уже ушли в других заказах, возвращены не будут.',
      )
    ) {
      return;
    }
    setBusyId(orderId);
    setError(null);
    try {
      const result = await restoreOrderItems(orderId);
      setPage((current) =>
        current
          ? {...current, content: current.content.map((item) => (item.id === orderId ? result.order : item))}
          : current,
      );
      const skipped = result.skippedNames.length
        ? ` Пропущено: ${result.skippedNames.join('; ')}.`
        : '';
      setFeedback(`Заказ #${orderId}: возвращено в продажу позиций — ${result.restoredCount}.${skipped}`);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyId(null);
    }
  }

  const allStatuses = statuses.length ? statuses : FALLBACK_STATUSES;

  return (
    <section className='page'>
      <h1 className='page__title'>Все заказы (админ)</h1>

      <div className='chips' role='group' aria-label='Фильтр заказов по статусу'>
        <button type='button' className={`chip ${status === '' ? 'is-active' : ''}`} onClick={() => filterBy('')}>
          все
        </button>
        {allStatuses.map((value) => (
          <button
            key={value}
            type='button'
            className={`chip ${status === value ? 'is-active' : ''}`}
            onClick={() => filterBy(value)}
          >
            {statusLabel(value)}
          </button>
        ))}
      </div>

      {error && <p className='alert alert--error'>{error.message}</p>}
      {feedback && <p className='alert alert--success'>{feedback}</p>}
      {!page && <Spinner />}

      {page && (
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
                <th></th>
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
                    <div className='muted'>
                      индекс {order.postalCode} · {order.phoneNumber}
                    </div>
                  </td>
                  <td>
                    <OrderItemsList items={order.items} variant='admin' />
                  </td>
                  <td>
                    {/* сумму берём из ответа сервера: в ней уже учтена скидка промокода */}
                    {money(order.total)}
                    {order.promoCode && (
                      <div className='muted'>
                        промокод {order.promoCode} −{order.discountPercent}% (скидка {money(order.discountAmount)})
                      </div>
                    )}
                  </td>
                  <td>{dateTime(order.createdAt)}</td>
                  <td>
                    <select
                      value={order.status}
                      disabled={busyId === order.id}
                      onChange={(event) => handleStatus(order.id, event.target.value)}
                      aria-label={`Статус заказа ${order.id}`}
                    >
                      {allStatuses.map((value) => (
                        <option key={value} value={value}>
                          {statusLabel(value)}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className='table__actions'>
                    {order.status === 'CANCELED' ? (
                      <button
                        type='button'
                        className='btn btn--primary btn--small'
                        onClick={() => handleRestoreItems(order.id)}
                        disabled={busyId === order.id}
                        title='Позиции вернутся в каталог, кроме уже проданных в других заказах'
                      >
                        {busyId === order.id ? 'Возвращаем…' : 'вернуть товары в продажу'}
                      </button>
                    ) : (
                      <span className='muted'>—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {page && page.content.length === 0 && <p className='empty'>Заказов с таким статусом нет.</p>}
      {page && <Pagination page={page} onChange={setPageNumber} />}
    </section>
  );
}
