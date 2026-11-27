import {Link} from 'react-router-dom';
import type {PromoCodeUsageDto} from '../../types/api';
import {dateTime, money, percent, statusLabel} from '../../utils/format';

/**
 * Отчёт «как работали промокоды».
 *
 * Что важно понимать, глядя на эти цифры:
 *  - «упущенная выгода» (discountAmount) — реальные деньги, которые магазин НЕ получил.
 *    Это не расход и не возврат, а именно скидка;
 *  - считаются только НЕотменённые заказы: отменённая скидка деньги не стоила;
 *  - данные берутся из «слепков» в заказе (promo_code, discount_percent), поэтому
 *    отчёт не переписывается задним числом, если админ удалит или переименует код.
 *
 * Список заказов под каждым кодом свёрнут в <details>: так таблица остаётся
 * читаемой на десятках кодов, но проверить конкретный заказ можно одним кликом
 * без самописного аккордеона и лишнего состояния в React.
 */
export function PromoUsagePanel({usages}: {usages: PromoCodeUsageDto[]}) {
  return (
    <section className='panel'>
      <h2 className='page__title page__title--small'>Использование промокодов</h2>

      {usages.length === 0 && (
        <p className='empty'>Промокодами пока не пользовались (или все заказы с ними отменены).</p>
      )}

      {usages.length > 0 && (
        <div className='table-wrap'>
          <table className='table'>
            <thead>
              <tr>
                <th>Код</th>
                <th>Раз применён</th>
                <th>Сумма до скидки</th>
                <th>Итог</th>
                <th title='Сколько магазин уступил покупателю'>Упущенная выгода</th>
                <th>Средняя скидка</th>
              </tr>
            </thead>
            <tbody>
              {usages.map((usage) => (
                <tr key={usage.code}>
                  <td>
                    <b>{usage.code}</b>
                    <details className='usage__details'>
                      <summary>заказы ({usage.orders.length})</summary>
                      <ul className='usage__orders'>
                        {usage.orders.map((order) => (
                          <li key={order.orderId}>
                            <Link to={`/orders/${order.orderId}`}>#{order.orderId}</Link>{' '}
                            <span className={`badge badge--${order.status.toLowerCase()}`}>
                              {statusLabel(order.status)}
                            </span>{' '}
                            <span className='muted'>
                              −{order.discountPercent}% · скидка {money(order.discountAmount)} · итог{' '}
                              {money(order.total)} · {order.customerEmail ?? 'без покупателя'} ·{' '}
                              {dateTime(order.createdAt)}
                            </span>
                          </li>
                        ))}
                      </ul>
                    </details>
                  </td>
                  <td>{usage.timesUsed}</td>
                  <td>{money(usage.ordersSubtotal)}</td>
                  <td>{money(usage.ordersTotal)}</td>
                  <td className='warn-text'>{money(usage.discountAmount)}</td>
                  <td>
                    {/* средняя скидка в процентах: сколько в среднем «уступили» с заказа */}
                    {percent(usage.ordersSubtotal > 0 ? (usage.discountAmount / usage.ordersSubtotal) * 100 : 0)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
