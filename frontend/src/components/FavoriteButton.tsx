import {useState, type MouseEvent} from 'react';
import {useAuth} from '../context/AuthContext';
import {useFavorites} from '../context/FavoritesContext';

/**
 * Кнопка «сердечко» (избранное).
 *
 * Работает через FavoritesContext, поэтому состояние синхронизировано между
 * каталогом, страницей товара и профилем: закрасили сердце в каталоге — оно
 * закрашено и в списке избранного.
 *
 * preventDefault + stopPropagation нужны внутри карточки товара: сердечко лежит
 * поверх области, ведущей на страницу товара, и клик не должен никуда вести.
 *
 * Сердце закрашивается сразу (оптимистично), а если запрос на сервер не прошёл —
 * контекст вернёт состояние обратно и покажет текст ошибки.
 */
export function FavoriteButton({tshirtId, className = ''}: {tshirtId: number; className?: string}) {
  const {isFavorite, toggle} = useFavorites();
  const {user} = useAuth();
  const [busy, setBusy] = useState(false);

  const active = isFavorite(tshirtId);
  const label = active ? 'Убрать из избранного' : 'Добавить в избранное';

  async function handleClick(event: MouseEvent<HTMLButtonElement>) {
    event.preventDefault();
    event.stopPropagation();
    if (busy) {
      return;
    }
    setBusy(true);
    try {
      await toggle(tshirtId);
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      type='button'
      className={`fav ${active ? 'is-favorite' : ''} ${className}`.trim()}
      onClick={handleClick}
      disabled={busy}
      aria-pressed={active}
      aria-label={label}
      title={user ? label : 'Войдите в аккаунт, чтобы сохранять избранное'}
    >
      {/* Одна и та же иконка: outline -> залитое сердце меняет только CSS
          (.fav.is-favorite svg {fill: currentColor}), так что анимация плавная */}
      <svg viewBox='0 0 24 24' aria-hidden='true' focusable='false'>
        <path d='M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z' />
      </svg>
    </button>
  );
}
