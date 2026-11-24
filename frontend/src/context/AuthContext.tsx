import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  login as apiLogin,
  me as apiMe,
  register as apiRegister,
  verifyEmail as apiVerifyEmail,
  type Credentials,
  type RegisterPayload,
  type VerifyEmailPayload,
} from '../api/auth';
import { logoutRequest, onSessionLost } from '../api/http';
import { clearTokens, getRefreshToken, saveTokens } from '../auth/tokenStorage';
import type { AuthResponse, UserDto, VerificationStatusDto } from '../types/api';

/**
 * Контекст авторизации. Хранит текущего пользователя и предоставляет
 * login/register/verify-email/logout. Восстановление сессии: при загрузке страницы у нас
 * остались токены в localStorage -> просим /auth/me; если access-токен протух,
 * интерцептор в api/http.ts сам обновит пару токенов и повторит запрос.
 *
 * ВАЖНО про шапку сайта: login и verifyEmail обязаны вызвать setUser. Одних токенов
 * в localStorage недостаточно — Header (и любой компонент на useAuth) рисует имя,
 * «Мои заказы», «Админку» и «Выйти» по состоянию React. Без setUser пользователь
 * оставался бы «гостем» до перезагрузки страницы, которая заново читает сессию
 * через /auth/me.
 *
 * Порядок с подтверждением e-mail: register НЕ логинит (токенов в ответе нет) —
 * он возвращает статус «ждём PIN-код». Войти получится только после verifyEmail.
 */

interface AuthContextValue {
  user: UserDto | null;
  /** true, пока идёт попытка восстановить сессию (чтобы не мигал «войдите»). */
  initializing: boolean;
  login: (credentials: Credentials) => Promise<UserDto>;
  /** Регистрация: токенов не возвращает, только статус подтверждения. */
  register: (payload: RegisterPayload) => Promise<VerificationStatusDto>;
  /** Ввод 6-значного PIN-кода из письма: после него пользователь считается вошедшим. */
  verifyEmail: (payload: VerifyEmailPayload) => Promise<UserDto>;
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

  /**
   * Регистрация только создаёт аккаунт и отправляет PIN-код: пользователем мы
   * становимся НЕ здесь (user не трогаем), а после подтверждения e-mail.
   */
  const register = useCallback(async (payload: RegisterPayload) => {
    return apiRegister(payload);
  }, []);

  /**
   * Подтверждение e-mail: бэкенд проверил PIN — значит аккаунт активирован и
   * выдал пару токенов. Дальше ведут себя как после логина.
   */
  const verifyEmail = useCallback(async (payload: VerifyEmailPayload) => {
    const session = applySession(await apiVerifyEmail(payload));
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
      verifyEmail,
      logout,
    }),
    [user, initializing, login, register, verifyEmail, logout],
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
