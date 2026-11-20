import axios, {AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig} from 'axios';
import type {ApiErrorBody} from '../types/api';
import {clearTokens, getAccessToken, getRefreshToken, saveTokens} from '../auth/tokenStorage';

/**
 * Единая Axios-«коробка» для всех запросов к API.
 *
 * Как это работает (ключевая часть всего фронтенда):
 *  1) request-интерцептор подставляет заголовок Authorization: Bearer <access>.
 *     Ни одна страница не помнит про токены — заголовок навешивается автоматически.
 *  2) response-интерцептор ловит 401: если у нас есть refresh-токен, один раз
 *     вызывает /api/auth/refresh, получает новую пару токенов и ПОВТОРЯЕТ
 *     исходный запрос. Пользователь не замечает, что access-токен протух.
 *  3) если обновить нельзя (refresh отозван/истёк) — сессия очищается,
 *     а AuthContext получает событие «сессия потеряна» и уводит на /login.
 *
 * Refresh выполняется через отдельный axios-инстанс без интерцепторов, иначе
 * получился бы бесконечный цикл (401 на запрос refresh -> снова refresh).
 */

export const API_BASE = import.meta.env.VITE_API_URL ?? '/api';

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

export const http = axios.create({baseURL: API_BASE, timeout: 15000});

/** Отдельный клиент для /auth/refresh — без интерцепторов. */
const plain = axios.create({baseURL: API_BASE, timeout: 15000});

let refreshInFlight: Promise<string> | null = null;
const sessionLostHandlers = new Set<() => void>();

export function onSessionLost(handler: () => void): () => void {
  sessionLostHandlers.add(handler);
  return () => sessionLostHandlers.delete(handler);
}

function notifySessionLost(): void {
  clearTokens();
  sessionLostHandlers.forEach((handler) => handler());
}

/** Ответ с бэкенда в формате ApiError; если пришёл не JSON — message = текст ошибки. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly fields: Record<string, string>;

  constructor(body: ApiErrorBody | undefined, status: number, fallback: string, localFields?: Record<string, string>) {
    super(body?.message ?? fallback);
    this.name = 'ApiRequestError';
    this.status = status;
    this.fields = {...(body?.fields ?? {}), ...(localFields ?? {})};
  }
}

export function toApiError(error: unknown): ApiRequestError {
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiErrorBody | undefined;
    const status = error.response?.status ?? 0;
    if (status === 0) {
      return new ApiRequestError(undefined, 0, 'Сервер недоступен. Проверьте, запущен ли бэкенд.');
    }
    return new ApiRequestError(body, status, error.message);
  }
  return new ApiRequestError(undefined, 0, error instanceof Error ? error.message : 'Неизвестная ошибка');
}

http.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Один и тот же refresh для всех параллельных 401 (shared promise). */
async function refreshOnce(): Promise<string> {
  if (!refreshInFlight) {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      return Promise.reject(new Error('Нет refresh-токена'));
    }
    refreshInFlight = plain
      .post<{accessToken: string; refreshToken: string}>('/auth/refresh', {refreshToken})
      .then((response) => {
        saveTokens({
          accessToken: response.data.accessToken,
          refreshToken: response.data.refreshToken,
        });
        return response.data.accessToken;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined;
    const status = error.response?.status;
    const isAuthEndpoint = config?.url?.includes('/auth/');

    if (status === 401 && config && !config._retried && !isAuthEndpoint && getRefreshToken()) {
      config._retried = true;
      try {
        const accessToken = await refreshOnce();
        config.headers.Authorization = `Bearer ${accessToken}`;
        return await http.request(config);
      } catch {
        notifySessionLost();
      }
    }
    return Promise.reject(error);
  },
);

/** Универсальные обёртки: меньше повторяющегося кода в модулях API. */
export async function getJson<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.get<T>(url, {...config, params});
  return response.data;
}

export async function postJson<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.post<T>(url, body);
  return response.data;
}

export async function patchJson<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.patch<T>(url, body);
  return response.data;
}

export async function putJson<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.put<T>(url, body);
  return response.data;
}

export async function deleteResource(url: string): Promise<void> {
  await http.delete(url);
}

/** Ссылка на картинку для <img src>. Эндпоинт публичный, токен не нужен. */
export function imageUrl(imageId: number | null | undefined): string | undefined {
  return imageId ? `${API_BASE}/images/${imageId}` : undefined;
}

export function logoutRequest(refreshToken: string): Promise<unknown> {
  return plain.post('/auth/logout', {refreshToken});
}
