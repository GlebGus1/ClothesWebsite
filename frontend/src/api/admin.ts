import {getJson, patchJson, postJson} from './http';
import type {OrderDto, OrderStatus, PageResponse, RestoreItemsResult, UserDto} from '../types/api';

/** Всё в этом модуле доступно только роли ADMIN (проверяет бэкенд, см. config/SecurityConfig.java). */

/**
 * Заказы админкой. `status` — фильтр по статусу ('ALL' или undefined — без фильтра).
 * Фильтрует база, поэтому пагинация честная: страница всегда содержит ровно
 * pageSize подходящих заказов, а не «страница всех заказов, из которых в Java
 * отфильтровали лишние».
 */
export const adminOrders = (page = 0, pageSize = 20, status?: string): Promise<PageResponse<OrderDto>> =>
  getJson<PageResponse<OrderDto>>('/admin/orders', {page, pageSize, status});

export const changeOrderStatus = (orderId: number, status: OrderStatus): Promise<OrderDto> =>
  patchJson<OrderDto>(`/admin/orders/${orderId}/status`, {status});

/** Справочник статусов с сервера — чтобы не держать enum в двух местах. */
export const orderStatuses = (): Promise<string[]> => getJson<string[]>('/admin/orders/statuses');

/**
 * Сознательный возврат позиций ОТМЕНЁННОГО заказа в продажу.
 *
 * Это отдельная операция, а не побочный эффект смены статуса: отмена заказа не
 * означает, что вещь физически свободна (её могли не вернуть, обменять, отправить
 * повторно). Поэтому в UI есть отдельная кнопка с подтверждением.
 */
export const restoreOrderItems = (orderId: number): Promise<RestoreItemsResult> =>
  postJson<RestoreItemsResult>(`/admin/orders/${orderId}/restore-items`);

export const adminUsers = (): Promise<UserDto[]> => getJson<UserDto[]>('/admin/users');

/**
 * Заблокировать (banned=true) или разблокировать (banned=false) аккаунт.
 * При блокировке сервер заодно отзывает все refresh-токены пользователя, поэтому
 * его текущая сессия умирает сама, без «напоминания» в браузере.
 */
export const setUserBanned = (userId: number, banned: boolean): Promise<UserDto> =>
  patchJson<UserDto>(`/admin/users/${userId}/ban`, {banned});
