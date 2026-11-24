import {deleteResource, getJson, postJson, putJson} from './http';
import type {PromoCodeDto, PromoCodeForm, PromoValidationResult} from '../types/api';

/**
 * Промокоды.
 *
 * validate — ручка для покупателя: сервер НЕ только «одобряет» код, но и заново
 * считает суммы по ценам из базы (tshirtIds в запросе нужны именно для этого).
 * Поэтому цифра скидки в корзине — серверная, а не из localStorage.
 */

export const validatePromoCode = (code: string, tshirtIds: number[]): Promise<PromoValidationResult> =>
  postJson<PromoValidationResult>('/promo-codes/validate', {code, tshirtIds});

export const adminPromoCodes = (): Promise<PromoCodeDto[]> => getJson<PromoCodeDto[]>('/admin/promo-codes');

/** code можно оставить пустым — тогда бэкенд сгенерирует читаемый код сам. */
export const createPromoCode = (form: PromoCodeForm): Promise<PromoCodeDto> =>
  postJson<PromoCodeDto>('/admin/promo-codes', form);

export const updatePromoCode = (id: number, form: PromoCodeForm): Promise<PromoCodeDto> =>
  putJson<PromoCodeDto>(`/admin/promo-codes/${id}`, form);

export const deletePromoCode = (id: number): Promise<void> => deleteResource(`/admin/promo-codes/${id}`);
