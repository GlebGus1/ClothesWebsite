import {getJson} from './http';
import type {AnalyticsDashboardDto, PromoCodeUsageDto} from '../types/api';

/**
 * Отчёты администратора (AdminAnalyticsController). Доступ к пути /api/admin/**
 * закрыт ролью ADMIN на сервере, поэтому здесь никаких «секретных» параметров нет.
 *
 * Важный принцип: цифры считает сервер одним-двумя запросами. Фронтенд НЕ тянет
 * всю историю заказов, чтобы посчитать выручку «в forEach» — иначе отчёт на
 * тысячах заказов превратится в скачивание базы в браузер.
 */

export const analyticsDashboard = (): Promise<AnalyticsDashboardDto> =>
  getJson<AnalyticsDashboardDto>('/admin/analytics/dashboard');

/** Как использовались промокоды: сколько раз, на какие суммы и какая упущенная выгода. */
export const promoCodeUsage = (): Promise<PromoCodeUsageDto[]> =>
  getJson<PromoCodeUsageDto[]>('/admin/analytics/promo-usage');
