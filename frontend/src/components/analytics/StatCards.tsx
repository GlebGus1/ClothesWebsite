/**
 * Плитки с ключевыми цифрами (выручка, скидки, заказы…).
 *
 * Числа приходят с сервера в готовом виде: фронтенд не пересчитывает выручку
 * из списка заказов. Причина не только в скорости, но и в правиле «один источник
 * правды»: отменённые заказы, скидки промокодов и округления уже учтены на бэкенде,
 * и если фронтенд начнёт считать по-своему, отчёт в админке разойдётся с деньгами.
 */
export interface StatCard {
  label: string;
  value: string;
  hint?: string;
  /** Подсветка «плохой» цифры (например, сумма скидок) — только цвет, без логики. */
  tone?: 'default' | 'warn' | 'good';
}

export function StatCards({cards}: {cards: StatCard[]}) {
  return (
    <div className='stats'>
      {cards.map((card) => (
        <div className={`stat stat--${card.tone ?? 'default'}`} key={card.label}>
          <span className='stat__label'>{card.label}</span>
          <b className='stat__value'>{card.value}</b>
          {card.hint && <span className='stat__hint'>{card.hint}</span>}
        </div>
      ))}
    </div>
  );
}
