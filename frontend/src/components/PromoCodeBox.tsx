import {useState, type FormEvent} from 'react';
import type {PromoValidationResult} from '../types/api';
import {money, shortDate} from '../utils/format';

/**
 * Поле промокода (корзина и оформление). Компонент ТОЛЬКО рисует форму и результат:
 * запросом к серверу занимается хук usePromo, а состоянием владеет страница.
 * Так одна и та же логика проверки не копируется в двух местах.
 */
export function PromoCodeBox({
  savedCode,
  applied,
  error,
  busy,
  disabled,
  onApply,
  onClear,
}: {
  savedCode: string | null;
  applied: PromoValidationResult | null;
  error: string | null;
  busy: boolean;
  disabled: boolean;
  onApply: (code: string) => void;
  onClear: () => void;
}) {
  const [code, setCode] = useState(savedCode ?? '');

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = code.trim();
    if (!trimmed) {
      return;
    }
    onApply(trimmed);
  }

  if (applied) {
    return (
      <div className='promo'>
        <div className='alert alert--success'>
          <p className='alert__title'>
            Промокод {applied.code} применён: −{applied.discountPercent}%
          </p>
          <p>{applied.message}</p>
          {applied.expiresAt && <p className='form__hint'>код действует до {shortDate(applied.expiresAt)}</p>}
          <button type='button' className='btn btn--ghost btn--small' onClick={onClear} disabled={busy}>
            убрать промокод
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className='promo'>
      <form className='promo__form' onSubmit={handleSubmit}>
        <label className='field'>
          <span>Промокод (если есть)</span>
          <input
            value={code}
            onChange={(event) => setCode(event.target.value.toUpperCase())}
            maxLength={40}
            placeholder='SALE10'
            autoComplete='off'
          />
        </label>
        <button type='submit' className='btn btn--ghost' disabled={busy || disabled}>
          {busy ? 'Проверяем…' : 'применить'}
        </button>
      </form>

      {error && <p className='promo__error'>{error}</p>}
    </div>
  );
}

/**
 * Строки итогов: товары / скидка / к оплате.
 *
 * subtotal — сумма «из корзины» (локальная), promo.totals — посчитанная сервером.
 * Если они разошлись (цену в localStorage подправили, цена в базе изменилась),
 * честно показываем серверную: заказ считается по базе, а не по фронтенду.
 */
export function PromoTotals({
  subtotal,
  promo,
}: {
  subtotal: number;
  promo: PromoValidationResult | null;
}) {
  if (!promo) {
    return null;
  }
  return (
    <div className='promo__totals'>
      <p>
        Товары <b>{money(promo.totals.subtotal)}</b>
      </p>
      <p className='promo__discount'>
        Скидка {promo.discountPercent}% <b>−{money(promo.totals.discountAmount)}</b>
      </p>
      <p className='promo__total'>
        К оплате <b>{money(promo.totals.total)}</b>
      </p>
      {Math.abs(promo.totals.subtotal - subtotal) > 0.005 && (
        <p className='form__hint'>
          Сервер посчитал сумму по ценам из базы: {money(promo.totals.subtotal)}, а не {money(subtotal)}.
        </p>
      )}
    </div>
  );
}
