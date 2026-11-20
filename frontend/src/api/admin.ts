import {getJson, patchJson} from './http';
import type {OrderDto, OrderStatus, PageResponse, UserDto} from '../types/api';

/** Всё в этом модуле доступно только роли ADMIN (проверяет бэкенд, см. config/SecurityConfig.java). */

export const adminOrders = (page = 0, pageSize = 20): Promise<PageResponse<OrderDto>> =>
  getJson<PageResponse<OrderDto>>('/admin/orders', {page, pageSize});

export const changeOrderStatus = (orderId: number, status: OrderStatus): Promise<OrderDto> =>
  patchJson<OrderDto>(`/admin/orders/${orderId}/status`, {status});

export const orderStatuses = (): Promise<string[]> => getJson<string[]>('/admin/orders/statuses');

export const adminUsers = (): Promise<UserDto[]> => getJson<UserDto[]>('/admin/users');
