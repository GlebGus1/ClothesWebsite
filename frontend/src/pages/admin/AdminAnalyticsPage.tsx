import {useCallback, useEffect, useState} from 'react';
import {analyticsDashboard, promoCodeUsage} from '../../api/adminAnalytics';
import {toApiError, type ApiRequestError} from '../../api/http';
import {FunnelPanel} from '../../components/analytics/FunnelPanel';
import {PromoUsagePanel} from '../../components/analytics/PromoUsagePanel';
import {StatCards, type StatCard} from '../../components/analytics/StatCards';
import {TopProductsPanel} from '../../components/analytics/TopProductsPanel';
import {Spinner} from '../../components/Spinner';
import type {AnalyticsDashboardDto, PromoCodeUsageDto} from '../../types/api';
import {money} from '../../utils/format';

/**
 * Дашборд «Статистика» (/admin/analytics).
 *
 * Здесь ровно два запроса: /admin/analytics/dashboard и /admin/analytics/promo-usage.
 * Оба считают всё на сервере (SQL-агрегации + один обход позиций), потому что
 * «скачать все заказы в браузер и там посчитать выручку» на реальной базе — это
 * и медленная админка, и случайная выдача чужих заказов в JSON.
 *
 * Сама страница только рисует и подписывает цифры. Все правила (что такое выручка,
 * какие заказы не считать, как считать конверсии) живут в AnalyticsService — там же,
 * где и их unit-тесты.
 */
export function AdminAnalyticsPage() {
  const [dashboard, setDashboard] = useState<AnalyticsDashboardDto | null>(null);
  const [usages, setUsages] = useState<PromoCodeUsageDto[] | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);

  const load = useCallback(() => {
    setError(null);
    return Promise.all([analyticsDashboard(), promoCodeUsage()])
      .then(([stats, promo]) => {
        setDashboard(stats);
        setUsages(promo);
      })
      .catch((err) => setError(toApiError(err)));
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (error && !dashboard) {
    return <p className='alert alert--error'>{error.message}</p>;
  }
  if (!dashboard || !usages) {
    return <Spinner />;
  }

  const cards: StatCard[] = [
    {
      label: 'Выручка',
      value: money(dashboard.revenue),
      hint: 'все неотменённые заказы, уже со скидками промокодов',
      tone: 'good',
    },
    {
      label: 'Скидки промокодов',
      value: money(dashboard.discountsAmount),
      hint: 'упущенная выгода: сколько уступили покупателям',
      tone: 'warn',
    },
    {
      label: 'Сумма до скидок',
      value: money(dashboard.subtotal),
      hint: 'сумма позиций всех неотменённых заказов',
    },
    {
      label: 'Заказы',
      value: String(dashboard.ordersCount),
      hint: `отменённых: ${dashboard.cancelledOrdersCount}`,
    },
    {label: 'Товаров в каталоге', value: String(dashboard.productsCount), hint: 'без снятых с продажи'},
    {label: 'Покупателей', value: String(dashboard.customersCount), hint: 'у кого был хотя бы один заказ'},
  ];

  return (
    <section className='page'>
      <div className='page__head'>
        <h1 className='page__title'>Статистика и аналитика</h1>
        <button type='button' className='btn btn--ghost' onClick={() => void load()}>
          обновить
        </button>
      </div>

      {error && <p className='alert alert--error'>{error.message}</p>}

      <StatCards cards={cards} />

      <div className='two-columns'>
        <FunnelPanel funnel={dashboard.funnel} />
        <TopProductsPanel title='Топ по просмотрам' items={dashboard.mostViewed} metricLabel='просмотров' />
      </div>

      <div className='two-columns'>
        <TopProductsPanel title='Топ по добавлениям в избранное' items={dashboard.mostFavorited} metricLabel='в избранном' />
        <div className='panel'>
          <h2 className='page__title page__title--small'>Как читать эти цифры</h2>
          <ul className='notes'>
            <li>
              Выручка не включает отменённые заказы: деньги по отменённым заказам магазин не получил,
              а возврат товаров в продажу — отдельная ручка в админке заказов.
            </li>
            <li>
              Просмотры карточек уникальны: один посетитель засчитывает не больше одного просмотра
              товара в сутки, поэтому накрутить счётчик обновлением страницы не выйдет.
            </li>
            <li>
              Визиты, просмотры и корзина появляются только у согласившихся посетителей: отказ в
              cookie-баннере отключает сбор целиком, а не «обезличивает» его.
            </li>
            <li>
              Воронка может выглядеть странной после включения согласий: сначала заказов будет больше,
              чем добавлений в корзину, потому что история статистики не восстанавливается задним числом.
            </li>
          </ul>
        </div>
      </div>

      <PromoUsagePanel usages={usages} />
    </section>
  );
}
