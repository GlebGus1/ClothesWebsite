import {postJson, getJson} from './http';
import type {AuthResponse, CaptchaStatus, UserDto, VerificationStatusDto} from '../types/api';

export interface Credentials {
  email: string;
  password: string;
}

export interface RegisterPayload extends Credentials {
  firstName: string;
  lastName: string;
  /**
   * Токен Cloudflare Turnstile. Обязателен только если капча включена на сервере
   * (см. captchaStatus()): источник правды — настройки бэкенда, а не фронтенд.
   */
  captchaToken?: string | null;
}

export interface VerifyEmailPayload {
  email: string;
  /** 6 цифр из письма. */
  code: string;
}

export const login = (payload: Credentials): Promise<AuthResponse> => postJson<AuthResponse>('/auth/login', payload);

/**
 * Нужна ли капча на форме регистрации. Отвечает сервер: если Turnstile не настроен
 * (локальная разработка, CI), required=false — и виджет в форме не появляется,
 * а ключ siteKey фронтенд не хранит у себя вообще.
 */
export const captchaStatus = (): Promise<CaptchaStatus> => getJson<CaptchaStatus>('/auth/captcha-status');

/**
 * Регистрация: аккаунт создаётся НЕподтверждённым, токенов в ответе нет.
 * Дальше фронтенд идёт на форму ввода PIN-кода (verifyEmail).
 */
export const register = (payload: RegisterPayload): Promise<VerificationStatusDto> =>
  postJson<VerificationStatusDto>('/auth/register', payload);

/** Ввод PIN-кода. Только после него бэкенд выдаёт пару токенов. */
export const verifyEmail = (payload: VerifyEmailPayload): Promise<AuthResponse> =>
  postJson<AuthResponse>('/auth/verify-email', payload);

/** «Письмо не пришло» — просим сервер сгенерировать и отправить новый код. */
export const resendVerificationCode = (email: string): Promise<VerificationStatusDto> =>
  postJson<VerificationStatusDto>('/auth/verification-code', {email});

/** Вызывать только при активном access-токене (или после удачного refresh). */
export const me = (): Promise<UserDto> => getJson<UserDto>('/auth/me');
