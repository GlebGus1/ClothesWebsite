import {percent} from '../../utils/format';
import type {AnalyticsDashboardDto} from '../../types/api';

type Funnel = AnalyticsDashboardDto['funnel'];

/**
 * Воронка продаж: визиты → просмотры карточек → добавления в корзину → заказы.
 *
 * Откуда берутся числа: первые три шага — из таблицы tracking_events (её наполняет
 * фронтенд, и ТОЛЬКО если посетитель согласился в cookie-баннере), заказы — из
 * таблицы orders. Поэтому при пустом баннере согласий воронка пустая — это нормально,
 * а не ошибка: мы не считаем людей, которые отказались от статистики.
 *
 * Конверсий два: «от предыдущего шага» (где именно отвалились люди) и «от визитов»
 * (общая конверсия сайта). Деление на ноль исключено на сервере, поэтому здесь
 * не нужны проверки «а вдруг visits = 0».
 */
export function FunnelPanel({funnel}: {funnel: Funnel}) {
  // ширина полоски — относительно первого шага; если шага нет, делим на 1,
  // чтобы не получить NaN в style (полоски просто будут нулевой ширины)
  const base = Math.max(funnel.visits, 1);

  return (
    <section className='panel funnel'>
      <h2 className='page__title page__title--small'>Воронка продаж</h2>

      {funnel.visits === 0 && (
        <p className='alert alert--success'>
          Счётчик визитов пуст: либо сайт ещё никто не посещал с согласившимся браузером,
          либо все отказались в баннере согласий. Заказы при этом считаются всегда —
          они берутся из базы, а не из статистики.
        </p>
      )}

      <ol className='funnel__steps'>
        {funnel.steps.map((step, index) => (
          <li className='funnel__step' key={step.key}>
            <div className='funnel__head'>
              <span className='funnel__label'>
                {index + 1}. {step.label}
              </span>
              <b className='funnel__value'>{step.value}</b>
            </div>
            <div className='funnel__bar'>
              <span
                className='funnel__fill'
                style={{width: `${Math.min(100, (step.value / base) * 100)}%`}}
                aria-hidden='true'
              />
            </div>
            <p className='funnel__conversions muted'>
              от предыдущего шага: {percent(step.conversionFromPrevious)} · от визитов:{' '}
              {percent(step.conversionFromVisits)}
            </p>
            {/* конверсия > 100% невозможна «в вакууме»: значит, часть пути человек
                прошёл без согласия на статистику (заказы-то мы видим из базы) */}
            {step.conversionFromPrevious > 100 && (
              <p className='funnel__note'>
                Шагов больше, чем предыдущий: часть посетителей отказалась от статистики,
                поэтому их просмотры и корзина в воронке не видны, а заказ посчитан.
              </p>
            )}
          </li>
        ))}
      </ol>
    </section>
  );
}
