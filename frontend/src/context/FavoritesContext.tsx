import {createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode} from 'react';
import {addFavorite, favoriteIds, removeFavorite} from '../api/favorites';
import {toApiError} from '../api/http';
import {useAuth} from './AuthContext';

/**
 * Избранное («сердечко» в каталоге и на странице товара).
 *
 * В отличие от корзины, избранное ХРАНИТСЯ НА СЕРВЕРЕ (таблица user_favorites):
 * список привязан к аккаунту, поэтому он не теряется при чистке localStorage и
 * виден с любого устройства. Отсюда и оптимистичное обновление: сердце закрашиваем
 * сразу, а если запрос не прошёл — откатываем состояние и показываем текст ошибки.
 *
 * Гость избранным не пользуется: все ручки /api/favorites требуют токен, поэтому
 * у гостя список пустой, а toggle возвращает false (UI подсказывает «войдите»).
 */
interface FavoritesContextValue {
  /** id избранных товаров — их достаточно, чтобы закрасить сердечки во всём каталоге. */
  ids: number[];
  /** false, пока список ещё грузится: не показываем «пустое избранное» вместо загрузки. */
  loaded: boolean;
  isFavorite: (id: number) => boolean;
  /** Возвращает актуальное состояние после переключения (true — теперь в избранном). */
  toggle: (id: number) => Promise<boolean>;
  reload: () => Promise<void>;
  error: string | null;
  /** Убрать сообщение об ошибке (пользователь нажал «×»). */
  dismissError: () => void;
}

const FavoritesContext = createContext<FavoritesContextValue | null>(null);

export function FavoritesProvider({children}: {children: ReactNode}) {
  const {user} = useAuth();
  const [ids, setIds] = useState<number[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Смена пользователя (вход/выход) — грузим список заново; гостю просить нечего.
  useEffect(() => {
    setError(null);
    if (!user) {
      setIds([]);
      setLoaded(true);
      return;
    }
    setLoaded(false);
    favoriteIds()
      .then(setIds)
      .catch((err) => setError(toApiError(err).message))
      .finally(() => setLoaded(true));
  }, [user]);

  const reload = useCallback(async () => {
    if (!user) {
      setIds([]);
      return;
    }
    try {
      setIds(await favoriteIds());
    } catch (err) {
      setError(toApiError(err).message);
    }
  }, [user]);

  const toggle = useCallback<FavoritesContextValue['toggle']>(
    async (id) => {
      if (!user) {
        setError('Сохранять избранное можно только после входа в аккаунт');
        return false;
      }

      const wasFavorite = ids.includes(id);
      // оптимистично: сердечко реагирует на нажатие мгновенно
      setIds((current) => (wasFavorite ? current.filter((favoriteId) => favoriteId !== id) : [...current, id]));
      setError(null);

      try {
        if (wasFavorite) {
          await removeFavorite(id);
        } else {
          await addFavorite(id);
        }
        return !wasFavorite;
      } catch (err) {
        // откат — иначе UI врёт пользователю про несделанное действие
        setIds((current) => (wasFavorite ? [...current, id] : current.filter((favoriteId) => favoriteId !== id)));
        setError(toApiError(err).message);
        return wasFavorite;
      }
    },
    [ids, user],
  );

  const isFavorite = useCallback((id: number) => ids.includes(id), [ids]);

  const dismissError = useCallback(() => setError(null), []);

  const value = useMemo<FavoritesContextValue>(
    () => ({ids, loaded, isFavorite, toggle, reload, error, dismissError}),
    [ids, loaded, isFavorite, toggle, reload, error, dismissError],
  );

  return <FavoritesContext.Provider value={value}>{children}</FavoritesContext.Provider>;
}

export function useFavorites(): FavoritesContextValue {
  const context = useContext(FavoritesContext);
  if (!context) {
    throw new Error('useFavorites можно вызывать только внутри <FavoritesProvider>');
  }
  return context;
}
