import {getJson, postJson} from './http';
import type {CreateOrderPayload, OrderDto, PageResponse} from '../types/api';

/** Заказ создаём от имени текущего пользователя: бэкенд берёт id из токена. */
export const createOrder = (payload: CreateOrderPayload): Promise<OrderDto> =>
  postJson<OrderDto>('/orders', payload);

export const myOrders = (page = 0, pageSize = 10): Promise<PageResponse<OrderDto>> =>
  getJson<PageResponse<OrderDto>>('/orders/me', {page, pageSize});

export const getOrder = (id: number): Promise<OrderDto> => getJson<OrderDto>(`/orders/${id}`);
