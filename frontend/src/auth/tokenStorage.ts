/**
 * Хранилище токенов в браузере.
 *
 * ВАЖНО ДЛЯ УЧЁБЫ: здесь access- и refresh-токены лежат в localStorage.
 * Это простой и понятный вариант для SPA, но у него есть цена: любой XSS-скрипт
 * на странице умеет прочитать localStorage. Более строгий вариант — refresh-токен
 * в httpOnly-cookie (его не видит JS), а access-токен держать только в памяти.
 *
 * Что сделано здесь, чтобы было безопаснее «по умолчанию»:
 *  1) в БД хранится не сам refresh-токен, а его SHA-256-хэш (бэкенд);
 *  2) refresh-токен одноразовый: после /api/auth/refresh старый отзывается (ротация);
 *  3) при 401 и неудачном обновлении сессия полностью стирается (см. api/http.ts).
 */

const ACCESS_KEY = 'clothes.accessToken';
const REFRESH_KEY = 'clothes.refreshToken';

export interface StoredTokens {
  accessToken: string;
  refreshToken: string;
}

export function saveTokens(tokens: StoredTokens): void {
  localStorage.setItem(ACCESS_KEY, tokens.accessToken);
  localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}
