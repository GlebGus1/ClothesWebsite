import {Link, NavLink, useNavigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import {useCart} from '../context/CartContext';

const link = ({isActive}: {isActive: boolean}) => `nav__link ${isActive ? 'is-active' : ''}`;

export function Header() {
  const {user, logout} = useAuth();
  const {count} = useCart();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/catalog');
  }

  return (
    <header className='header'>
      <div className='header__inner'>
        <Link to='/catalog' className='logo'>
          Clothes<span>Shop</span>
        </Link>

        <nav className='nav'>
          <NavLink to='/catalog' className={link}>
            Каталог
          </NavLink>
          <NavLink to='/cart' className={link}>
            Корзина {count > 0 && <span className='pill'>{count}</span>}
          </NavLink>

          {user && (
            <>
              <NavLink to='/profile' className={link}>
                Профиль
              </NavLink>
              <NavLink to='/orders' className={link}>
                Мои заказы
              </NavLink>
            </>
          )}
          {user?.admin && (
            <>
              <NavLink to='/admin/products' className={link}>
                Товары
              </NavLink>
              <NavLink to='/admin/orders' className={link}>
                Заказы
              </NavLink>
              <NavLink to='/admin/promo-codes' className={link}>
                Промокоды
              </NavLink>
              <NavLink to='/admin/collections' className={link}>
                Коллекции
              </NavLink>
              <NavLink to='/admin/analytics' className={link}>
                Статистика
              </NavLink>
              <NavLink to='/admin/users' className={link}>
                Пользователи
              </NavLink>
            </>
          )}
        </nav>

        <div className='header__user'>
          {user ? (
            <>
              {/* Имя — ссылка на профиль: так у пользователя есть вход в его заказы
                  и избранное, а не только в шапке «Мои заказы» */}
              <Link to='/profile' className='header__name' title={`${user.email} — мой профиль`}>
                {user.firstName} {user.lastName}
                {user.admin && <span className='badge badge--admin'>admin</span>}
              </Link>
              <button type='button' className='btn btn--ghost' onClick={handleLogout}>
                Выйти
              </button>
            </>
          ) : (
            <>
              <Link to='/login' className='btn btn--ghost'>
                Войти
              </Link>
              <Link to='/register' className='btn btn--primary'>
                Регистрация
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
