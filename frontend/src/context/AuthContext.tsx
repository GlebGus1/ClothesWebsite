import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { login as apiLogin, me as apiMe, register as apiRegister, type Credentials, type RegisterPayload } from '../api/auth';
import { logoutRequest, onSessionLost } from '../api/http';
import { clearTokens, getRefreshToken, saveTokens } from '../auth/tokenStorage';
import type { AuthResponse, UserDto } from '../types/api';

/**
 * Контекст авторизации. Хранит текущего пользователя и предоставляет
 * login/register/logout. Восстановление сессии: при загрузке страницы у нас
 * остались токены в localStorage -> просим /auth/me; если access-токен протух,
 * интерцептор в api/http.ts сам обновит пару токенов и повторит запрос.
 *
 * ВАЖНО про шапку сайта: login и register обязаны вызвать setUser. Одних токенов
 * в localStorage недостаточно — Header (и любой компонент на useAuth) рисует имя,
 * «Мои заказы», «Админку» и «Выйти» по состоянию React. Без setUser пользователь
 * оставался бы «гостем» до перезагрузки страницы, которая заново читает сессию
 * через /auth/me.
 */

interface AuthContextValue {
  user: UserDto | null;
  /** true, пока идёт попытка восстановить сессию (чтобы не мигал «войдите»). */
  initializing: boolean;
  login: (credentials: Credentials) => Promise<UserDto>;
  register: (payload: RegisterPayload) => Promise<UserDto>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function applySession(response: AuthResponse): UserDto {
  saveTokens({accessToken: response.accessToken, refreshToken: response.refreshToken});
  return response.user;
}

export function AuthProvider({children}: {children: ReactNode}) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [initializing, setInitializing] = useState(true);

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      // отзыв refresh-токена на сервере: даже украденный токен становится бесполезен
      await logoutRequest(refreshToken).catch(() => undefined);
    }
    clearTokens();
    setUser(null);
  }, []);

  /**
   * Логин возвращает пару токенов и пользователя. Сохраняем токены (пережить
   * перезагрузку) И обновляем состояние (шапка должна поменяться сразу, без
   * перезагрузки и без ручного обновления контекста).
   */
  const login = useCallback(async (credentials: Credentials) => {
    const session = applySession(await apiLogin(credentials));
    setUser(session);
    return session;
  }, []);

  /** Регистрация сразу логинит: новый пользователь видит себя в шапке. */
  const register = useCallback(async (payload: RegisterPayload) => {
    const session = applySession(await apiRegister(payload));
    setUser(session);
    return session;
  }, []);

  useEffect(() => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      setInitializing(false);
      return;
    }
    apiMe()
      .then(setUser)
      .catch(() => {
        clearTokens();
        setUser(null);
      })
      .finally(() => setInitializing(false));
  }, []);

  useEffect(
    () =>
      onSessionLost(() => {
        setUser(null);
      }),
    [],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      initializing,
      login,
      register,
      logout,
    }),
    [user, initializing, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth можно вызывать только внутри <AuthProvider>');
  }
  return context;
}
