import {useSearchParams} from 'react-router-dom';
import {FavoritesPanel} from '../components/FavoritesPanel';
import {OrdersPanel} from '../components/OrdersPanel';
import {Spinner} from '../components/Spinner';
import {useAuth} from '../context/AuthContext';
import {useFavorites} from '../context/FavoritesContext';

/**
 * Профиль пользователя (/profile).
 *
 * Две вкладки: «Мои заказы» и «Избранное». Активную вкладку держим в URL
 * (?tab=favorites), а не в useState — тогда страницу можно обновить или отправить
 * ссылку другу, и останется открытой нужная вкладка. Плюс это готовая «глубокая»
 * ссылка: шапка может вести сразу в избранное.
 *
 * Сами данные дают компоненты-панели: OrdersPanel работает с /api/orders/me,
 * FavoritesPanel — с /api/favorites. Пользователя они не передают: бэкенд берёт его
 * из токена, поэтому чужие заказы или чужое избранное здесь открыть нельзя.
 */
type TabKey = 'orders' | 'favorites';

const TABS: {key: TabKey; label: string}[] = [
  {key: 'orders', label: 'Мои заказы'},
  {key: 'favorites', label: 'Избранное'},
];

export function ProfilePage() {
  const {user} = useAuth();
  const {ids} = useFavorites();
  const [searchParams, setSearchParams] = useSearchParams();

  const tab: TabKey = searchParams.get('tab') === 'favorites' ? 'favorites' : 'orders';

  function openTab(next: TabKey) {
    // «Мои заказы» — вкладка по умолчанию, поэтому её в URL не держим (ссылка короче)
    setSearchParams(next === 'orders' ? {} : {tab: next}, {replace: true});
  }

  if (!user) {
    return <Spinner label='Загружаем профиль…' />;
  }

  return (
    <section className='page profile'>
      <h1 className='page__title'>Профиль</h1>

      <div className='profile__card'>
        <div>
          <p className='profile__name'>
            {user.firstName} {user.lastName}
            {user.admin && <span className='badge badge--admin'>admin</span>}
          </p>
          <p className='form__hint'>{user.email}</p>
        </div>
        <span className='badge badge--muted'>{user.admin ? 'доступ администратора' : 'покупатель'}</span>
      </div>

      <div className='tabs' role='tablist'>
        {TABS.map((item) => (
          <button
            key={item.key}
            type='button'
            role='tab'
            aria-selected={tab === item.key}
            className={`tabs__tab ${tab === item.key ? 'is-active' : ''}`}
            onClick={() => openTab(item.key)}
          >
            {item.label}
            {item.key === 'favorites' && ids.length > 0 && <span className='pill'>{ids.length}</span>}
          </button>
        ))}
      </div>

      {tab === 'orders' ? <OrdersPanel /> : <FavoritesPanel />}
    </section>
  );
}
