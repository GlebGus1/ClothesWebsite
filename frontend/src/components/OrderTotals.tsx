import type {OrderDto} from '../types/api';
import {money} from '../utils/format';

/**
 * Деньги заказа (покупатель и админ видят одно и то же).
 *
 * Все числа — из ответа сервера: OrderMapper уже посчитал сумму по ценам из БД и
 * вычел скидку промокода. Поэтому в UI нет своей арифметики скидок — иначе цифра
 * на экране могла бы разойтись с цифрой в заказе.
 *
 * Строку «Товары» восстанавливаем как total + discountAmount: бэкенд гарантирует,
 * что total = subtotal − discount (см. TotalsDto.of).
 */
export function OrderTotals({order}: {order: OrderDto}) {
  const discount = order.discountAmount ?? 0;
  const subtotal = order.total + discount;

  return (
    <div className='order-totals'>
      <p>
        <span>Товары</span>
        <b>{money(subtotal)}</b>
      </p>
      {order.promoCode && (
        <p className='order-totals__discount'>
          <span>
            Промокод {order.promoCode} · −{order.discountPercent}%
          </span>
          <b>−{money(discount)}</b>
        </p>
      )}
      <p className='order-totals__total'>
        <span>Итого</span>
        <b>{money(order.total)}</b>
      </p>
    </div>
  );
}
