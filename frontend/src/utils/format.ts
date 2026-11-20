import type {OrderStatus} from '../types/api';

export const money = (value: number | null | undefined): string =>
  new Intl.NumberFormat('ru-RU', {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(value ?? 0) + ' ₴';

export const dateTime = (value: string | null | undefined): string =>
  value ? new Date(value).toLocaleString('ru-RU', {dateStyle: 'short', timeStyle: 'short'}) : '—';

/** Человекочитаемые статусы заказа (значения перечисления Order.OrderStatus). */
export const STATUS_LABELS: Record<OrderStatus, string> = {
  PROCESSING: 'Принимается',
  IN_TRANSIT: 'В пути',
  DELIVERED: 'Доставлен',
  CANCELED: 'Отменён',
};

export const statusLabel = (status: string): string =>
  STATUS_LABELS[status as OrderStatus] ?? status;

export const fileSize = (bytes: number): string => `${(bytes / 1024).toFixed(0)} КБ`;
