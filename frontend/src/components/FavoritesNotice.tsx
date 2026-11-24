import {Link} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import {useFavorites} from '../context/FavoritesContext';

/**
 * Сообщение об ошибке избранного (например, гость нажал на сердечко, или сервер
 * вернул ошибку сети). Один компонент вместо двух копий: его ставят каталог и
 * страница товара, и выглядит он одинаково.
 *
 * Гостю дополнительно подсказываем ссылку «войти»: избранное живёт в базе и
 * привязано к аккаунту, без токена сердце не работает.
 */
export function FavoritesNotice() {
  const {error, dismissError} = useFavorites();
  const {user} = useAuth();

  if (!error) {
    return null;
  }

  return (
    <p className='alert alert--error favorites-notice'>
      <span>{error}</span>
      {!user && (
        <Link to='/login' className='btn btn--ghost btn--small'>
          войти
        </Link>
      )}
      <button type='button' className='notice__close' onClick={dismissError} aria-label='Скрыть сообщение'>
        ×
      </button>
    </p>
  );
}
