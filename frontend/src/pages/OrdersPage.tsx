import {OrdersPanel} from '../components/OrdersPanel';

/**
 * Страница «Мои заказы» (/orders). Вся таблица вынесена в OrdersPanel, потому что
 * тот же список нужен вкладкой в профиле — дублировать разметку и запросы незачем.
 */
export function OrdersPage() {
  return (
    <section className='page'>
      <h1 className='page__title'>Мои заказы</h1>
      <OrdersPanel />
    </section>
  );
}
