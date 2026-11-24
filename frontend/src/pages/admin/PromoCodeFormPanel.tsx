import {useState, type FormEvent} from 'react';
import type {PromoCodeDto, PromoCodeForm} from '../../types/api';

/**
 * Форма создания/правки промокода (админка).
 *
 * Компонент только собирает значения и проверяет их «на глаз» (1–99 процентов):
 * финальная валидация всё равно на бэкенде (@Min/@Max в PromoCodeFormRequest),
 * а её текст приходит в поле errors.
 *
 * Поле «код» можно оставить пустым — тогда бэкенд сгенерирует короткий читаемый
 * код сам (без букв O/0 и I/1, чтобы код не путался при переписывании от руки).
 */
export function PromoCodeFormPanel({
  initial,
  busy,
  onSubmit,
  onCancel,
}: {
  initial: PromoCodeDto | null;
  busy: boolean;
  onSubmit: (form: PromoCodeForm) => void;
  onCancel: () => void;
}) {
  const [code, setCode] = useState(initial?.code ?? '');
  const [percent, setPercent] = useState(initial ? String(initial.discountPercent) : '');
  const [active, setActive] = useState(initial ? initial.active : true);
  const [expiresAt, setExpiresAt] = useState(toInputValue(initial?.expiresAt ?? null));
  const [localError, setLocalError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = Number(percent);
    if (!Number.isInteger(value) || value < 1 || value > 99) {
      setLocalError('Скидка — целое число от 1 до 99 процентов');
      return;
    }
    setLocalError(null);
    onSubmit({
      code: code.trim().toUpperCase(),
      discountPercent: value,
      active,
      expiresAt: fromInputValue(expiresAt),
    });
  }

  return (
    <form className='form promo-code-form' onSubmit={handleSubmit}>
      <h2 className='page__title page__title--small'>
        {initial ? `Промокод #${initial.id}` : 'Новый промокод'}
      </h2>

      {localError && <p className='alert alert--error'>{localError}</p>}

      <label className='field'>
        <span>Код (пусто — сгенерируем)</span>
        <input
          value={code}
          onChange={(event) => setCode(event.target.value.toUpperCase())}
          maxLength={40}
          pattern='[A-Za-z0-9\-_]*'
          placeholder='SALE10'
          autoComplete='off'
        />
      </label>

      <label className='field'>
        <span>Скидка, % (1–99)</span>
        <input
          value={percent}
          onChange={(event) => setPercent(event.target.value.replace(/\D/g, ''))}
          inputMode='numeric'
          maxLength={2}
          required
        />
      </label>

      <label className='field'>
        <span>Срок действия (пусто — бессрочно)</span>
        <input type='datetime-local' value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
      </label>

      <label className='checkbox'>
        <input type='checkbox' checked={active} onChange={(event) => setActive(event.target.checked)} />
        <span>код активен</span>
      </label>

      <div className='page__actions'>
        <button type='submit' className='btn btn--primary' disabled={busy}>
          {busy ? 'Сохраняем…' : 'сохранить'}
        </button>
        <button type='button' className='btn btn--ghost' onClick={onCancel} disabled={busy}>
          отмена
        </button>
      </div>
    </form>
  );
}

/**
 * Мгновенное время (Instant, ISO с «Z») -> значение для input[type=datetime-local].
 * Браузер показывает поле в своём часовом поясе, поэтому переводим вручную:
 * getHours/getMinutes дают локальные часы, а «Z» при отправке подставит Date.
 */
function toInputValue(iso: string | null): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Наоборот: локальное поле -> ISO-строка, которую понимает Jackson (Instant). */
function fromInputValue(value: string): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
