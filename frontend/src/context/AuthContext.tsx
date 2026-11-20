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
      login: async (credentials) => applySession(await apiLogin(credentials)),
      register: async (payload) => applySession(await apiRegister(payload)),
      logout,
    }),
    [user, initializing, logout],
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
